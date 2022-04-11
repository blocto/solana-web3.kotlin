# SolanaWeb3

[![Maven Central](https://img.shields.io/maven-central/v/com.portto/solana.web3.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.portto%22%20AND%20a:%22solana.web3%22)
[![CircleCI](https://circleci.com/gh/portto/solana-web3.kotlin/tree/master.svg?style=svg)](https://circleci.com/gh/portto/solana-web3.kotlin/tree/master)
![GitHub](https://img.shields.io/github/license/portto/solana-web3.kotlin)

This is a open source library on kotlin for Solana protocol.


## How to
```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'com.portto.solana:web3:0.1.1 
}
```

## Example

```kotlin
val api = Connection(Cluster.DEVNET)

val account = KeyPair.fromSecretKey(walletPrivateKey.decodeBase58())
val instructions = SystemProgram.transfer(
    fromPublicKey = PublicKey("58ewYwS4XiZo5VuspKDdYkgi82n9carELJ63oGb7AZUq"),
    toPublicKey = PublicKey("ENkttsgNeYUJ1HFVHs77c4tqMqyWE3WeHMMkiZ1hkr7x"),
    lamports = 10000
)

val transaction = Transaction().add(instructions)
val txSignature = api.sendTransaction(
    transaction = transaction,
    signers = listOf(account)
)

println(txSignature)
```

### Developed By

Kihon, <kihon@portto.com>


### License

MIT. Original by [Arturo Jamaica](https://github.com/ajamaica/Solana.kt), forked and maintained by [portto](https://github.com/portto/).