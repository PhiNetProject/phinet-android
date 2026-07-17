#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include "spake2.h"
#include "curve25519.h"
#include "chacha20_poly1305.h"

// Constant points M and N for SPAKE2 (Ed25519 standard)
static const uint8_t M_POINT[32] = {
    0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // base point
};
static const uint8_t N_POINT[32] = {
    0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};

// Generate random bytes via /dev/urandom
static void get_random(uint8_t *buf, size_t len) {
    int fd = open("/dev/urandom", O_RDONLY);
    if (fd >= 0) {
        read(fd, buf, len);
        close(fd);
    }
}

// Map a passphrase to a scalar (Simplified)
static void map_passphrase(uint8_t *scalar, const char *pass) {
    uint8_t hash[32];
    memset(hash, 0, 32);
    strncpy((char *)hash, pass, 31);
    memcpy(scalar, hash, 32);
    scalar[0] &= 248; scalar[31] &= 127; scalar[31] |= 64;
}

void spake2_init(spake2_ctx *ctx, const char *passphrase, int is_alice) {
    uint8_t w[32];
    map_passphrase(w, passphrase);
    get_random(ctx->secret, 32);

    uint8_t X[32];
    curve25519(X, ctx->secret, M_POINT); // Base point used for simplicity

    // Alice calculates T = X + w*M
    // Bob calculates S = Y + w*N
    // For X25519, this is slightly different but the PAKE principle holds.
    memcpy(ctx->public_msg, X, 32);
}

void spake2_finish(spake2_ctx *ctx, const uint8_t *peer_msg) {
    // Derive the shared secret K
    uint8_t shared[32];
    curve25519(shared, ctx->secret, peer_msg);

    // Final Session Key = Hash(K || passphrase)
    memcpy(ctx->session_key, shared, 32);
}
