/* Copyright (c) 2025-present The Q-BitX Core developers. MIT License.
 * Bounded JNI boundary. Secret copies are local and erased on every exit path. */
#include <jni.h>
#include <stdint.h>
#include "api.h"
#include "params.h"
#include "sign.h"

static void wipe(void *data, size_t length) {
    volatile uint8_t *p = data;
    while (length--) *p++ = 0;
}

static jobjectArray key_arrays(JNIEnv *env, const uint8_t *pk, const uint8_t *sk) {
    jclass cls = (*env)->FindClass(env, "[B");
    if (cls == NULL) return NULL;
    jobjectArray result = (*env)->NewObjectArray(env, 2, cls, NULL);
    if (result == NULL) return NULL;
    jbyteArray pub = (*env)->NewByteArray(env, CRYPTO_PUBLICKEYBYTES);
    if (pub == NULL) return NULL;
    jbyteArray priv = (*env)->NewByteArray(env, CRYPTO_SECRETKEYBYTES);
    if (priv == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, pub, 0, CRYPTO_PUBLICKEYBYTES, (const jbyte*)pk);
    (*env)->SetByteArrayRegion(env, priv, 0, CRYPTO_SECRETKEYBYTES, (const jbyte*)sk);
    (*env)->SetObjectArrayElement(env, result, 0, pub);
    (*env)->SetObjectArrayElement(env, result, 1, priv);
    return (*env)->ExceptionCheck(env) ? NULL : result;
}

JNIEXPORT jobjectArray JNICALL
Java_org_qbitx_wallet_crypto_DilithiumNative_generateKeypair(JNIEnv *env, jobject thiz) {
    uint8_t pk[CRYPTO_PUBLICKEYBYTES], sk[CRYPTO_SECRETKEYBYTES];
    jobjectArray result = NULL;
    if (crypto_sign_keypair(pk, sk) == 0) result = key_arrays(env, pk, sk);
    wipe(sk, sizeof(sk));
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_org_qbitx_wallet_crypto_DilithiumNative_generateKeypairFromSeed(JNIEnv *env, jobject thiz, jbyteArray input) {
    if (input == NULL || (*env)->GetArrayLength(env, input) != SEEDBYTES) return NULL;
    uint8_t seed[SEEDBYTES], pk[CRYPTO_PUBLICKEYBYTES], sk[CRYPTO_SECRETKEYBYTES];
    (*env)->GetByteArrayRegion(env, input, 0, SEEDBYTES, (jbyte*)seed);
    jobjectArray result = NULL;
    if (!(*env)->ExceptionCheck(env) && crypto_sign_seed_keypair(pk, sk, seed) == 0)
        result = key_arrays(env, pk, sk);
    wipe(seed, sizeof(seed)); wipe(sk, sizeof(sk));
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_org_qbitx_wallet_crypto_DilithiumNative_sign(JNIEnv *env, jobject thiz, jbyteArray message, jbyteArray secretKey) {
    if (message == NULL || secretKey == NULL || (*env)->GetArrayLength(env, secretKey) != CRYPTO_SECRETKEYBYTES) return NULL;
    jsize length = (*env)->GetArrayLength(env, message);
    if (length > 1048576) return NULL;
    uint8_t sk[CRYPTO_SECRETKEYBYTES], sig[CRYPTO_BYTES];
    (*env)->GetByteArrayRegion(env, secretKey, 0, CRYPTO_SECRETKEYBYTES, (jbyte*)sk);
    if ((*env)->ExceptionCheck(env)) { wipe(sk, sizeof(sk)); return NULL; }
    jbyte *msg = (*env)->GetByteArrayElements(env, message, NULL);
    if (msg == NULL) { wipe(sk, sizeof(sk)); return NULL; }
    size_t siglen = 0;
    int rc = crypto_sign_signature(sig, &siglen, (const uint8_t*)msg, (size_t)length, NULL, 0, sk);
    wipe(sk, sizeof(sk));
    (*env)->ReleaseByteArrayElements(env, message, msg, JNI_ABORT);
    if (rc != 0 || siglen != CRYPTO_BYTES) return NULL;
    jbyteArray result = (*env)->NewByteArray(env, (jsize)siglen);
    if (result != NULL) (*env)->SetByteArrayRegion(env, result, 0, (jsize)siglen, (const jbyte*)sig);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_org_qbitx_wallet_crypto_DilithiumNative_verify(JNIEnv *env, jobject thiz, jbyteArray signature, jbyteArray message, jbyteArray publicKey) {
    if (signature == NULL || message == NULL || publicKey == NULL ||
        (*env)->GetArrayLength(env, signature) != CRYPTO_BYTES ||
        (*env)->GetArrayLength(env, publicKey) != CRYPTO_PUBLICKEYBYTES) return JNI_FALSE;
    jsize length = (*env)->GetArrayLength(env, message);
    if (length > 1048576) return JNI_FALSE;
    uint8_t sig[CRYPTO_BYTES], pk[CRYPTO_PUBLICKEYBYTES];
    (*env)->GetByteArrayRegion(env, signature, 0, CRYPTO_BYTES, (jbyte*)sig);
    (*env)->GetByteArrayRegion(env, publicKey, 0, CRYPTO_PUBLICKEYBYTES, (jbyte*)pk);
    if ((*env)->ExceptionCheck(env)) return JNI_FALSE;
    jbyte *msg = (*env)->GetByteArrayElements(env, message, NULL);
    if (msg == NULL) return JNI_FALSE;
    int rc = crypto_sign_verify(sig, CRYPTO_BYTES, (const uint8_t*)msg, (size_t)length, NULL, 0, pk);
    (*env)->ReleaseByteArrayElements(env, message, msg, JNI_ABORT);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_org_qbitx_wallet_crypto_DilithiumNative_getPublicKeyBytes(JNIEnv *env, jobject thiz) { return CRYPTO_PUBLICKEYBYTES; }
JNIEXPORT jint JNICALL Java_org_qbitx_wallet_crypto_DilithiumNative_getSecretKeyBytes(JNIEnv *env, jobject thiz) { return CRYPTO_SECRETKEYBYTES; }
JNIEXPORT jint JNICALL Java_org_qbitx_wallet_crypto_DilithiumNative_getSignatureBytes(JNIEnv *env, jobject thiz) { return CRYPTO_BYTES; }
