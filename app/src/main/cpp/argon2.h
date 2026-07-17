#ifndef ARGON2_H
#define ARGON2_H

#include <stdint.h>
#include <stddef.h>

/*
 * Argon2id implementation (Simplified for Lockr)
 * Based on RFC 9106
 */

#define ARGON2_BLOCK_SIZE 1024
#define ARGON2_QWORDS_IN_BLOCK (ARGON2_BLOCK_SIZE / 8)
#define ARGON2_SYNC_POINTS 4

typedef struct {
    uint64_t v[ARGON2_QWORDS_IN_BLOCK];
} block;

int argon2id_hash(
    uint8_t *out, uint32_t outlen,
    const uint8_t *pwd, uint32_t pwdlen,
    const uint8_t *salt, uint32_t saltlen,
    uint32_t t_cost, uint32_t m_cost, uint32_t lanes
);

#endif
