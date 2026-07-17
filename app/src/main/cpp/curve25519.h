#ifndef CURVE25519_H
#define CURVE25519_H

#include <stdint.h>

void curve25519(uint8_t *mypublic, const uint8_t *secret, const uint8_t *basepoint);

#endif
