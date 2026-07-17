#ifndef SPAKE2_H
#define SPAKE2_H

#include <stdint.h>

/**
 * SPAKE2 Implementation for "Invisible" Handshake
 * Based on RFC 9381 (Simplified for Ed25519 points)
 */

typedef struct {
    uint8_t secret[32];
    uint8_t public_msg[32];
    uint8_t session_key[32];
} spake2_ctx;

void spake2_init(spake2_ctx *ctx, const char *passphrase, int is_alice);
void spake2_finish(spake2_ctx *ctx, const uint8_t *peer_msg);

#endif
