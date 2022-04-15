package com.portto.solana.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.portto.solana.app.ui.theme.SolanaWeb3Theme
import com.portto.solana.web3.Connection
import com.portto.solana.web3.KeyPair
import com.portto.solana.web3.PublicKey
import com.portto.solana.web3.Transaction
import com.portto.solana.web3.programs.SystemProgram
import com.portto.solana.web3.rpc.types.config.TransactionRpcConfig
import com.portto.solana.web3.util.Cluster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.komputing.kbase58.decodeBase58


class MainActivity : ComponentActivity() {

    private val walletPrivateKey =
        "4hRb3KEYtiyqSewYpW1KA75EpHuHttJjnnA2yQfowTPdSRPPZHicUhkzMZpEEuAM5pkEdsqhnvLXPQ2pavbSWRAM"

    private val api by lazy { Connection(Cluster.DEVNET) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SolanaWeb3Theme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting("Android")
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Main()
                }
            }
        }
    }

    @Preview
    @Composable
    private fun Main() {
        Button(
            onClick = {
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        println(
                            api.getAccountInfo(
                                "58ewYwS4XiZo5VuspKDdYkgi82n9carELJ63oGb7AZUq"
                            )
                        )
                    }
                }
            }
        ) {
            Text(text = "getAccountInfo")
        }
        Button(
            onClick = {
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        println(
                            api.getTransaction(
                                signature = "3hemaWyvFYhtu36sqvQgRJPsbJQmq9pcR37SV6eKnZGS94ASLdz1u2SLkUtaHt768DZ9W3hXoWGLZ16n9sXd9FZH",
                                config = TransactionRpcConfig()
                            )
                        )
                    }
                }
            }
        ) {
            Text(text = "getTransaction")
        }
        Button(
            onClick = {
                val account = KeyPair.fromSecretKey(walletPrivateKey.decodeBase58())
                val instructions =
                    SystemProgram.transfer(
                        fromPublicKey = PublicKey("58ewYwS4XiZo5VuspKDdYkgi82n9carELJ63oGb7AZUq"),
                        toPublicKey = PublicKey("ENkttsgNeYUJ1HFVHs77c4tqMqyWE3WeHMMkiZ1hkr7x"),
                        lamports = "0.01".toBigDecimal().scaleByPowerOfTen(9).longValueExact()
                    )

                val transaction = Transaction().apply {
                    add(instructions)
                }
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        println(
                            api.sendTransaction(
                                transaction = transaction,
                                signers = listOf(account),
                                options = null
                            )
                        )
                    }
                }
            }
        ) {
            Text(text = "Transfer: 0.01 SOL")
        }
        Button(
            onClick = {
                api.onAccountChange(
                    PublicKey("58ewYwS4XiZo5VuspKDdYkgi82n9carELJ63oGb7AZUq"),
                    { accountInfo, context ->
                        println(
                            "lamports:${
                                accountInfo.lamports.toBigDecimal().scaleByPowerOfTen(-9).toPlainString()
                            }"
                        )
                        println(context.slot)
                    }
                )
                val account = KeyPair.fromSecretKey(walletPrivateKey.decodeBase58())
                val instructions =
                    ValueProgram.createSetValueInstruction(UInt.MAX_VALUE, account.publicKey)
                val transaction = Transaction().apply {
                    add(instructions)
                }
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        println(
                            api.sendTransaction(
                                transaction = transaction,
                                signers = listOf(account)
                            )
                        )
                    }
                }
            }
        ) {
            Text(text = "set value: 4294967295")
        }

        Button(
            content = { Text(text = "serializeMessage: 85089687") },
            onClick = {
                val walletAddress = PublicKey("ENkttsgNeYUJ1HFVHs77c4tqMqyWE3WeHMMkiZ1hkr7x")
                val instructions =
                    ValueProgram.createSetValueInstruction(85089687u, walletAddress)
                val transaction = Transaction().apply {
                    add(instructions)
                }
                lifecycleScope.launch {
                    withContext(Dispatchers.Default) {
                        api.getLatestBlockhash()?.let { blockhash ->
                            transaction.recentBlockhash = blockhash
                            transaction.feePayer = walletAddress
                            val serializedTransaction = transaction.serializeMessage()
                            println(serializedTransaction.asUByteArray()
                                .joinToString("") { it.toString(radix = 16).padStart(2, '0') })
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SolanaWeb3Theme {
        Greeting("Android")
    }
}