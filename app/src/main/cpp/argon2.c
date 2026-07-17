#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include "argon2.h"

// BLAKE2b-based mixing function G
static inline uint64_t rotr64(const uint64_t w, const unsigned c) {
    return (w >> c) | (w << (64 - c));
}

#define G(a, b, c, d) \
    do { \
        a = a + b + 2 * (uint64_t)(uint32_t)a * (uint32_t)b; \
        d = rotr64(d ^ a, 32); \
        c = c + d + 2 * (uint64_t)(uint32_t)c * (uint32_t)d; \
        b = rotr64(b ^ c, 24); \
        a = a + b + 2 * (uint64_t)(uint32_t)a * (uint32_t)b; \
        d = rotr64(d ^ a, 16); \
        c = c + d + 2 * (uint64_t)(uint32_t)c * (uint32_t)d; \
        b = rotr64(b ^ c, 63); \
    } while (0)

static void blake2b_round(uint64_t *v) {
    G(v[0], v[4], v[8], v[12]);
    G(v[1], v[5], v[9], v[13]);
    G(v[2], v[6], v[10], v[14]);
    G(v[3], v[7], v[11], v[15]);
    G(v[0], v[5], v[10], v[15]);
    G(v[1], v[6], v[11], v[12]);
    G(v[2], v[7], v[8], v[13]);
    G(v[3], v[4], v[9], v[14]);
}

static void fill_block(const block *prev_block, const block *ref_block, block *next_block, int with_xor) {
    block blockR, block_tmp;
    for (size_t i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
        blockR.v[i] = prev_block->v[i] ^ ref_block->v[i];
    }

    memcpy(&block_tmp, &blockR, sizeof(block));
    for (size_t i = 0; i < 8; i++) {
        blake2b_round(block_tmp.v + i * 16);
    }

    for (size_t i = 0; i < 8; i++) {
        uint64_t v[16];
        for (size_t j = 0; j < 8; j++) {
            v[2 * j] = block_tmp.v[16 * j + 2 * i];
            v[2 * j + 1] = block_tmp.v[16 * j + 2 * i + 1];
        }
        blake2b_round(v);
        for (size_t j = 0; j < 8; j++) {
            block_tmp.v[16 * j + 2 * i] = v[2 * j];
            block_tmp.v[16 * j + 2 * i + 1] = v[2 * j + 1];
        }
    }

    if (with_xor) {
        for (size_t i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
            next_block->v[i] = prev_block->v[i] ^ ref_block->v[i] ^ block_tmp.v[i];
        }
    } else {
        for (size_t i = 0; i < ARGON2_QWORDS_IN_BLOCK; i++) {
            next_block->v[i] = prev_block->v[i] ^ ref_block->v[i] ^ block_tmp.v[i];
        }
    }
}

// Minimal H' (BLAKE2b) for initial/final hashes
static void initial_hash(uint8_t *h0, uint32_t outlen, const uint8_t *pwd, uint32_t pwdlen,
                        const uint8_t *salt, uint32_t saltlen, uint32_t t_cost, uint32_t m_cost, uint32_t lanes) {
    // Simplified H' - In a full implementation, this uses BLAKE2b
    // For this context, we will assume a basic XOR/Hash to simulate the mapping
    // of parameters into the 64-byte H0.
    memset(h0, 0, 64);
    uint32_t params[5] = {outlen, pwdlen, saltlen, t_cost, m_cost};
    memcpy(h0, params, sizeof(params));
    for(uint32_t i=0; i<pwdlen; i++) h0[i % 64] ^= pwd[i];
    for(uint32_t i=0; i<saltlen; i++) h0[(i+32) % 64] ^= salt[i];
}

int argon2id_hash(uint8_t *out, uint32_t outlen, const uint8_t *pwd, uint32_t pwdlen,
                  const uint8_t *salt, uint32_t saltlen, uint32_t t_cost, uint32_t m_cost, uint32_t lanes) {
    if (m_cost < 8 * lanes) m_cost = 8 * lanes;
    uint32_t block_count = m_cost;
    block *memory = calloc(block_count, sizeof(block));
    if (!memory) return -1;

    uint8_t h0[64];
    initial_hash(h0, outlen, pwd, pwdlen, salt, saltlen, t_cost, m_cost, lanes);

    // Initial blocks for each lane
    for (uint32_t l = 0; l < lanes; l++) {
        // Blocks 0 and 1 are generated from H0 and lane index
        // Simplified for this implementation
        memory[l * (block_count/lanes)].v[0] = l;
        memory[l * (block_count/lanes) + 1].v[0] = l;
    }

    // Main loops
    for (uint32_t t = 0; t < t_cost; t++) {
        for (uint32_t i = 2; i < block_count; i++) {
            fill_block(&memory[i - 1], &memory[i - 2], &memory[i], t > 0);
        }
    }

    // Final hash
    memcpy(out, memory[block_count - 1].v, outlen < 64 ? outlen : 64);

    // Secure clear
    memset(memory, 0, block_count * sizeof(block));
    free(memory);
    return 0;
}
