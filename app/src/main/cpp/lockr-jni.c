#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include "chacha20_poly1305.h"
#include "argon2.h"
#include "spake2.h"

// Global context for the active handshake
static spake2_ctx g_spake_ctx;

// ─── JNI Helpers ─────────────────────────────────────────────────────────────

typedef struct {
    const char *in_path;
    const char *out_path;
    jbyte      *key;
    jbyte      *aad;
    size_t      aad_len;
} jni_args;

static void jni_acquire(JNIEnv *env, jstring in_path, jstring out_path,
                        jbyteArray key_bytes, jbyteArray aad_bytes,
                        jni_args *out)
{
    out->in_path  = (*env)->GetStringUTFChars(env, in_path,  NULL);
    out->out_path = (*env)->GetStringUTFChars(env, out_path, NULL);
    out->key      = (*env)->GetByteArrayElements(env, key_bytes, NULL);

    // Memory Safety: Lock the key in RAM to prevent swapping to disk
    jsize key_len = (*env)->GetArrayLength(env, key_bytes);
    mlock(out->key, (size_t)key_len);

    out->aad      = NULL;
    out->aad_len  = 0;

    if (aad_bytes != NULL) {
        out->aad_len = (size_t)(*env)->GetArrayLength(env, aad_bytes);
        out->aad     = (*env)->GetByteArrayElements(env, aad_bytes, NULL);
        mlock(out->aad, out->aad_len);
    }
}

static void jni_release(JNIEnv *env, jstring in_path, jstring out_path,
                        jbyteArray key_bytes, jbyteArray aad_bytes,
                        jni_args *args)
{
    jsize key_len = (*env)->GetArrayLength(env, key_bytes);

    // Memory Safety: Wipe sensitive data and unlock
    if (args->key) {
        memset(args->key, 0, (size_t)key_len);
        munlock(args->key, (size_t)key_len);
        (*env)->ReleaseByteArrayElements(env, key_bytes, args->key, JNI_ABORT);
    }

    if (args->aad) {
        memset(args->aad, 0, args->aad_len);
        munlock(args->aad, args->aad_len);
        (*env)->ReleaseByteArrayElements(env, aad_bytes, args->aad, JNI_ABORT);
    }

    (*env)->ReleaseStringUTFChars(env, in_path,  args->in_path);
    (*env)->ReleaseStringUTFChars(env, out_path, args->out_path);
}

static void throw_exception(JNIEnv *env, const char *msg) {
    jclass exClass = (*env)->FindClass(env, "java/io/IOException");
    if (exClass != NULL) {
        (*env)->ThrowNew(env, exClass, msg);
    }
}

// ─── JNI Entry Points ────────────────────────────────────────────────────────

JNIEXPORT jint JNICALL
Java_com_phinet_app_vault_NativeLib_encryptFile(JNIEnv *env, jobject thiz,
                                         jstring in_path, jstring out_path,
                                         jbyteArray key_bytes, jbyteArray aad_bytes)
{
    jni_args args;
    jni_acquire(env, in_path, out_path, key_bytes, aad_bytes, &args);

    FILE *fin, *fout;
    int result = -1;

    fin = fopen(args.in_path, "rb");
    fout = fopen(args.out_path, "wb");

    if (fin && fout) {
        uint8_t nonce[24], tag[16];
        int fd = open("/dev/urandom", O_RDONLY);
        if (fd >= 0) {
            if (read(fd, nonce, 24) == 24) {
                fseek(fin, 0, SEEK_END);
                size_t filesize = (size_t)ftell(fin);
                fseek(fin, 0, SEEK_SET);

                result = xchacha20poly1305_encrypt_stream_progress(
                    fin, fout, tag,
                    (uint8_t *)args.key, nonce,
                    (uint8_t *)args.aad, args.aad_len,
                    filesize
                );
            }
            close(fd);
        }
        fclose(fin);
        fclose(fout);
    } else {
        if (fin) fclose(fin);
        if (fout) fclose(fout);
        throw_exception(env, "Failed to open files for encryption");
    }

    jni_release(env, in_path, out_path, key_bytes, aad_bytes, &args);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_phinet_app_vault_NativeLib_decryptFile(JNIEnv *env, jobject thiz,
                                         jstring in_path, jstring out_path,
                                         jbyteArray key_bytes, jbyteArray aad_bytes)
{
    jni_args args;
    jni_acquire(env, in_path, out_path, key_bytes, aad_bytes, &args);

    FILE *fin, *fout;
    int result = -1;

    fin = fopen(args.in_path, "rb");
    fout = fopen(args.out_path, "wb");

    if (fin && fout) {
        result = xchacha20poly1305_decrypt_stream_progress(
            fin, fout,
            (uint8_t *)args.key,
            (uint8_t *)args.aad, args.aad_len
        );

        fclose(fin);
        fclose(fout);

        // If decryption or authentication failed, wipe the output file immediately
        if (result != 0) {
            remove(args.out_path);
            if (result == -2) {
                throw_exception(env, "Authentication failed: Invalid key or corrupted file.");
            } else {
                throw_exception(env, "Decryption failed: File system error.");
            }
        }
    } else {
        if (fin) fclose(fin);
        if (fout) fclose(fout);
        throw_exception(env, "Failed to open files for decryption");
    }

    jni_release(env, in_path, out_path, key_bytes, aad_bytes, &args);
    return result;
}

JNIEXPORT void JNICALL
Java_com_phinet_app_vault_NativeLib_secureClear(JNIEnv *env, jobject thiz, jobject buffer) {
    void *ptr = (*env)->GetDirectBufferAddress(env, buffer);
    jlong capacity = (*env)->GetDirectBufferCapacity(env, buffer);
    if (ptr && capacity > 0) {
        memset(ptr, 0, (size_t)capacity);
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_phinet_app_vault_NativeLib_spake2Init(JNIEnv *env, jobject thiz, jstring passphrase, jboolean is_alice) {
    const char *pass = (*env)->GetStringUTFChars(env, passphrase, NULL);
    mlock(pass, strlen(pass));

    spake2_init(&g_spake_ctx, pass, is_alice);

    jbyteArray res = (*env)->NewByteArray(env, 32);
    (*env)->SetByteArrayRegion(env, res, 0, 32, (jbyte *)g_spake_ctx.public_msg);

    munlock(pass, strlen(pass));
    (*env)->ReleaseStringUTFChars(env, passphrase, pass);
    return res;
}

JNIEXPORT jbyteArray JNICALL
Java_com_phinet_app_vault_NativeLib_spake2Finish(JNIEnv *env, jobject thiz, jbyteArray peer_msg) {
    jbyte *peer_ptr = (*env)->GetByteArrayElements(env, peer_msg, NULL);
    spake2_finish(&g_spake_ctx, (uint8_t *)peer_ptr);

    jbyteArray res = (*env)->NewByteArray(env, 32);
    (*env)->SetByteArrayRegion(env, res, 0, 32, (jbyte *)g_spake_ctx.session_key);

    (*env)->ReleaseByteArrayElements(env, peer_msg, peer_ptr, JNI_ABORT);
    // Securely clear global context after finish
    memset(&g_spake_ctx, 0, sizeof(spake2_ctx));
    return res;
}

JNIEXPORT jbyteArray JNICALL
Java_com_phinet_app_vault_NativeLib_argon2id(JNIEnv *env, jobject thiz,
                                      jbyteArray password, jbyteArray salt,
                                      jint t_cost, jint m_cost, jint parallelism, jint tag_len)
{
    jbyte *pwd_ptr  = (*env)->GetByteArrayElements(env, password, NULL);
    jbyte *salt_ptr = (*env)->GetByteArrayElements(env, salt, NULL);
    jsize pwd_len   = (*env)->GetArrayLength(env, password);
    jsize salt_len  = (*env)->GetArrayLength(env, salt);

    mlock(pwd_ptr, (size_t)pwd_len);
    mlock(salt_ptr, (size_t)salt_len);

    jbyteArray result = (*env)->NewByteArray(env, tag_len);
    jbyte *res_ptr    = (*env)->GetByteArrayElements(env, result, NULL);
    mlock(res_ptr, (size_t)tag_len);

    int status = argon2id_hash(
        (uint8_t *)res_ptr, (uint32_t)tag_len,
        (uint8_t *)pwd_ptr, (uint32_t)pwd_len,
        (uint8_t *)salt_ptr, (uint32_t)salt_len,
        (uint32_t)t_cost, (uint32_t)m_cost, (uint32_t)parallelism
    );

    munlock(pwd_ptr, (size_t)pwd_len);
    munlock(salt_ptr, (size_t)salt_len);

    (*env)->ReleaseByteArrayElements(env, password, pwd_ptr, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, salt, salt_ptr, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, result, res_ptr, 0);

    if (status != 0) return NULL;
    return result;
}
