package com.phinet.app.node

/**
 * ΦNET network parameters baked into the app. These make the phone a real
 * client node: it bootstraps to the backbone, trusts the directory
 * authorities, and pulls the consensus itself — no external daemon needed.
 */
object NodeConfig {
    // Backbone bootstrap peers. The consensus-driven guard logic dials the
    // rest of the network from the consensus, so one reachable entry is
    // enough, but we list both for resilience.
    val BOOTSTRAP = listOf(
        "phinetproject.com:7700",
        "lobarcs.com:7700",
        "libraryofaletheia.com:7700",
    )

    // Directory authority public keys (must match the network's authorities).
    val TRUSTED_AUTHORITIES = listOf(
        "af1aebff73f4bc25cb593481c78ca0b80f4c016237a1c896eff3656995f2cf3c",
        "7c30f0d91e8cb9263d13425e662f646fe50beaebceb84e1f3cc0fa525a6dc512",
        "901e2740560270bb128b5c4d0cb8666a2cc525f87a9b75fb31bc8d94f2332ce8",
    )

    // Plain-HTTP consensus URL (the daemon's fetcher does no TLS; integrity
    // comes from the authority signatures).
    const val CONSENSUS_URL = "http://phinetproject.com/phinet/consensus.json"

    // Local ports the embedded daemon listens on (loopback only).
    const val LINK_PORT = 7700
    const val CTL_PORT  = 7799
    const val COM_PORT  = 7801   // ctl_port + 2

    const val COM_BASE_URL = "http://127.0.0.1:$COM_PORT/"
}
