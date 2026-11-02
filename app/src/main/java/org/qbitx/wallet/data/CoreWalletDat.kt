package org.qbitx.wallet.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.qbitx.wallet.crypto.AddressUtils
import java.io.File
import java.security.MessageDigest

/** Real Core SQLite records, not a text backup renamed to .dat.
 * Imports all unencrypted PQ keys, bounded to 1000; never silently selects one key.
 */
object CoreWalletDat {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    // Mainnet network magic, verified against kernel/chainparams.cpp.
    const val networkMagic: Int = 0x51424954
    data class Recovery(val secret: ByteArray, val public: ByteArray, val seed: String?, val index: Int = 0) {
        fun clear() { secret.fill(0) }
    }
    private fun vector(bytes: ByteArray): ByteArray {
        require(bytes.size < 65536)
        return if(bytes.size < 253) byteArrayOf(bytes.size.toByte())+bytes
        else byteArrayOf(253.toByte(),bytes.size.toByte(),(bytes.size ushr 8).toByte())+bytes
    }
    private fun key(type: String, id: ByteArray=byteArrayOf()) = vector(type.toByteArray(Charsets.US_ASCII))+id
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun unpack(bytes: ByteArray, trailing: Int=0): ByteArray {
        require(bytes.isNotEmpty()) { "Truncated wallet record" }
        val first=bytes[0].toInt() and 255
        val offset=if(first==253) 3 else 1
        require(first < 254 && bytes.size >= offset) { "Unsupported wallet record length" }
        val size=if(first==253) (bytes[1].toInt() and 255) or ((bytes[2].toInt() and 255) shl 8) else first
        require((first!=253 || size>=253) && bytes.size==offset+size+trailing) { "Invalid wallet record length" }
        return bytes.copyOfRange(offset,offset+size)
    }
    private fun temporary(context: Context)=File.createTempFile("qbx-wallet-", ".dat", context.cacheDir)
    @Synchronized
    fun read(context: Context, bytes: ByteArray): List<Recovery> {
        require(bytes.size in 100..MAX_BYTES && bytes.copyOfRange(0,16).contentEquals(header)) {
            "Supported: unencrypted Q-BitX Core SQLite wallet.dat with PQ keys. Berkeley DB is not supported."
        }
        val file=temporary(context)
        try {
            file.writeBytes(bytes)
            SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA quick_check",null).use { require(it.moveToFirst() && it.getString(0)=="ok") { "Corrupt wallet database" } }
                db.rawQuery("PRAGMA application_id",null).use { require(it.moveToFirst() && it.getInt(0)==networkMagic) { "Wallet is for another network" } }
                db.rawQuery("PRAGMA user_version",null).use { require(it.moveToFirst() && it.getInt(0)==0) { "Unsupported wallet schema" } }
                val records=mutableMapOf<String,ByteArray>()
                db.rawQuery("SELECT key,value,length(key),length(value) FROM main LIMIT 100001",null).use { cursor ->
                    var count=0
                    while(cursor.moveToNext()) {
                        require(++count<=100000 && cursor.getInt(2) in 1..4096 && cursor.getInt(3) in 0..1048576) { "Wallet database limits exceeded" }
                        val k=cursor.getBlob(0); val typeLen=k[0].toInt() and 255
                        require(typeLen<253 && k.size>=typeLen+1) { "Unsupported wallet record" }
                        val type=String(k,1,typeLen,Charsets.US_ASCII)
                        require(type !in setOf("mkey","ckey","dilithiumckey","walletdescriptorckey")) { "Encrypted Core wallet.dat is not supported. Use an offline decrypted backup; never upload it." }
                        if(type in setOf("dilpk","dilsk","qbxseed")) {
                            require(cursor.getInt(3)<=8192) { "Oversized key record" }
                            records[AddressUtils.toHex(k)]=cursor.getBlob(1)
                        }
                    }
                }
                try {
                    val secrets=records.filterKeys { it.startsWith(AddressUtils.toHex(key("dilsk"))) }
                    require(secrets.size in 1..1000) { "Wallet must contain 1 to 1000 unencrypted PQ keys" }
                    val recovered=mutableListOf<Recovery>()
                    try {
                    for(entry in secrets.entries) {
                    val k=AddressUtils.fromHex(entry.key)
                    require(k.size==26) { "Invalid PQ key identifier" }
                    val id=k.copyOfRange(6,26)
                    val pub=unpack(requireNotNull(records[AddressUtils.toHex(key("dilpk",id))]) { "Missing PQ public key" })
                    require(pub.size==1952 && AddressUtils.hash160(pub).contentEquals(id)) { "PQ public key mismatch" }
                    val secret=unpack(entry.value,32)
                    try {
                        require(secret.size==4032) { "Unsupported PQ secret key" }
                        val checksum=entry.value.takeLast(32).toByteArray()
                        require(hash(pub+secret).contentEquals(checksum) || hash(id+secret).contentEquals(checksum)) { "PQ key checksum mismatch" }
                        val seedRecord=records[AddressUtils.toHex(key("qbxseed",id))] ?: if(secrets.size==1) records[AddressUtils.toHex(key("qbxseed"))] else null
                        val seed=seedRecord?.let { org.qbitx.wallet.crypto.SeedWallet.readBackupEnvelope(String(unpack(it),Charsets.UTF_8)) }
                        require(secrets.size==1 || !records.containsKey(AddressUtils.toHex(key("qbxseed")))) { "Ambiguous old seed metadata in multi-key wallet" }
                        val index=seedRecord?.let { org.qbitx.wallet.crypto.SeedWallet.backupIndex(String(unpack(it),Charsets.UTF_8)) } ?: 0
                        recovered.add(Recovery(secret.copyOf(),pub,seed,index))
                    } finally { secret.fill(0) }
                    }
                    require(records.keys.count { it.startsWith(AddressUtils.toHex(key("dilpk"))) } == recovered.size) { "Watch-only PQ keys cannot be recovered for spending" }
                    return recovered
                    } catch(e: Exception) { recovered.forEach { it.clear() }; throw e }
                } finally { records.values.forEach { it.fill(0) } }
            }
        } finally { file.delete(); File(file.path+"-journal").delete(); File(file.path+"-wal").delete(); File(file.path+"-shm").delete() }
    }
    @Synchronized
    fun write(context: Context, secret: ByteArray, public: ByteArray, seed: String?): ByteArray {
        return writeMany(context,listOf(Recovery(secret,public,seed)))
    }
    @Synchronized
    fun writeMany(context: Context, wallets: List<Recovery>): ByteArray {
        require(wallets.size in 1..1000)
        require(wallets.all { it.secret.size==4032 && it.public.size==1952 })
        val file=temporary(context)
        try {
            SQLiteDatabase.openOrCreateDatabase(file,null).use { db ->
                db.rawQuery("PRAGMA journal_mode=DELETE",null).use { require(it.moveToFirst() && it.getString(0).equals("delete",true)) }
                db.execSQL("PRAGMA application_id=$networkMagic")
                db.execSQL("PRAGMA user_version=0")
                db.execSQL("CREATE TABLE main(key BLOB PRIMARY KEY NOT NULL,value BLOB NOT NULL)")
                db.beginTransaction()
                try {
                    fun put(k: ByteArray,v: ByteArray) { try { db.execSQL("INSERT INTO main(key,value) VALUES(?,?)",arrayOf(k,v)) } finally { v.fill(0) } }
                    wallets.forEach { wallet ->
                    val public=wallet.public; val secret=wallet.secret; val seed=wallet.seed
                    val id=AddressUtils.hash160(public)
                    put(key("dilpk",id),vector(public))
                    put(key("dilsk",id),vector(secret)+hash(public+secret))
                    // Core ignores this optional mobile recovery metadata; PQ keys remain standard Core records.
                    if(seed!=null) put(key("qbxseed",id),vector(org.qbitx.wallet.crypto.SeedWallet.backupEnvelope(seed,wallet.index).toByteArray(Charsets.UTF_8)))
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            return file.readBytes()
        } finally { file.delete(); File(file.path+"-journal").delete() }
    }
}
