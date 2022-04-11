# SolanaWeb3

[![Maven Central](https://img.shields.io/maven-central/v/com.portto/solana.web3.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.portto%22%20AND%20a:%22solana.web3%22)
[![CircleCI](https://img.shields.io/circleci/build/github/portto/solana-web3.kotlin/master)](https://circleci.com/gh/portto/solana-web3.kotlin/tree/master)
![GitHub](https://img.shields.io/github/license/portto/solana-web3.kotlin)

This is a open source library on kotlin for Solana protocol.

**SolanaWeb3 that is currently under development, alpha builds are available in the [Sonatype staging repository](https://s01.oss.sonatype.org/content/repositories/staging/com/portto/solana/web3/).**

## How to
```gradle
repositories {
    mavenCentral()
    
    // If you need to get SolanaWeb3 versions that are not uploaded to Maven Central.
    maven { url "https://s01.oss.sonatype.org/content/repositories/staging/" }
}

dependencies {
    implementation 'com.portto.solana:web3:0.1.2'
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
