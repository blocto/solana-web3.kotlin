package com.portto.solana.web3

import com.portto.solana.web3.util.Shortvec
import org.junit.Assert
import org.junit.Test
import java.util.*

class ShortvecTest {

    private val encodedBase64Transaction =
        "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgEDCNjG8Ez1xvM/K4O6Pg6D6Ce7MFFXEFRCxypUTE5wxWix5ye16ypqZkH9JiPmgfkpXGXaFZmiJ/KDULMvEgo7m4yQBgdiNGkhiWWWv1TK7umW2+brORg/rxSsh1zqS2cvqQmULfJtUluUSjkFXvAx5fb0CizwfprKbefEiHBPiV309fwHUmtSkmp/WuUbzocnKDuvw5C0C2hYx/3zepD+AstjU7joYwTl/ceUs9HTLVSkAyFfWhiE4Xb07mWw6fpnqgVKU1D4XciC1hSlVnJ4iilt3x6rq9CmBniISTL07vagBt324ddloZPZy+FGzut5rBy0he1fWzeROoz1hX7/AKlw0hLu7g5TO9WMRPtzyfl6dCJ0b7Zeg0ZO3mwS7CUeIQMHBAMFAgEKDKCGAQAAAAAACQcEAwUEAQoMoIYBAAAAAAAJBgDwAnsibWVtbyI6eyJ0eXBlIjoic2RrdXNlcl90b19zZGt1c2VyIiwiZGF0YSI6IjcwMWU4ODBjZTgwZmFiZTI0YjRkZmFlMjg2ZTEyMTVjYTcwZTAxYmIwZGJhZDI0MGQ3OTMzNzNiZmMxYzRkOGFhNTlhOTM4YWZjNjk1YjM3MTA0MjQ0MzYxY2E1NWVmMjI5M2EyODM1YTE3NjAyYjU4ZjliNTI5NzI2YWU0MDMxNmYwOThiNDhhZDA0NTQ1YTQ3Mjk4N2RmZWE1NjViY2EzNDQxNmJmMjE0Mzk0MWViODU4YzQzNjAwNzdhODFjZDUwNzlmNWI2YzA4ZWEzMGQzNzJkNWVlZDIwM2U3YjNkYjFjMmMyZjk4NjBmMTZhMzIxOTY3YjBmZjU5MThmZTc3NzgxOTBkOWQwNzgwZTEwYmY4MGNkMDdiYmJmMWM4YjcwNGVkYTI3NTY5YmE4ZjdhYzY2OTFmNTc1ZjhmYzcwIn19"

    private val instructionDataBytes =
        "-16, 2, 123, 34, 109, 101, 109, 111, 34, 58, 123, 34, 116, 121, 112, 101, 34, 58, 34, 115, 100, 107, 117, 115, 101, 114, 95, 116, 111, 95, 115, 100, 107, 117, 115, 101, 114, 34, 44, 34, 100, 97, 116, 97, 34, 58, 34, 55, 48, 49, 101, 56, 56, 48, 99, 101, 56, 48, 102, 97, 98, 101, 50, 52, 98, 52, 100, 102, 97, 101, 50, 56, 54, 101, 49, 50, 49, 53, 99, 97, 55, 48, 101, 48, 49, 98, 98, 48, 100, 98, 97, 100, 50, 52, 48, 100, 55, 57, 51, 51, 55, 51, 98, 102, 99, 49, 99, 52, 100, 56, 97, 97, 53, 57, 97, 57, 51, 56, 97, 102, 99, 54, 57, 53, 98, 51, 55, 49, 48, 52, 50, 52, 52, 51, 54, 49, 99, 97, 53, 53, 101, 102, 50, 50, 57, 51, 97, 50, 56, 51, 53, 97, 49, 55, 54, 48, 50, 98, 53, 56, 102, 57, 98, 53, 50, 57, 55, 50, 54, 97, 101, 52, 48, 51, 49, 54, 102, 48, 57, 56, 98, 52, 56, 97, 100, 48, 52, 53, 52, 53, 97, 52, 55, 50, 57, 56, 55, 100, 102, 101, 97, 53, 54, 53, 98, 99, 97, 51, 52, 52, 49, 54, 98, 102, 50, 49, 52, 51, 57, 52, 49, 101, 98, 56, 53, 56, 99, 52, 51, 54, 48, 48, 55, 55, 97, 56, 49, 99, 100, 53, 48, 55, 57, 102, 53, 98, 54, 99, 48, 56, 101, 97, 51, 48, 100, 51, 55, 50, 100, 53, 101, 101, 100, 50, 48, 51, 101, 55, 98, 51, 100, 98, 49, 99, 50, 99, 50, 102, 57, 56, 54, 48, 102, 49, 54, 97, 51, 50, 49, 57, 54, 55, 98, 48, 102, 102, 53, 57, 49, 56, 102, 101, 55, 55, 55, 56, 49, 57, 48, 100, 57, 100, 48, 55, 56, 48, 101, 49, 48, 98, 102, 56, 48, 99, 100, 48, 55, 98, 98, 98, 102, 49, 99, 56, 98, 55, 48, 52, 101, 100, 97, 50, 55, 53, 54, 57, 98, 97, 56, 102, 55, 97, 99, 54, 54, 57, 49, 102, 53, 55, 53, 102, 56, 102, 99, 55, 48, 34, 125, 125"

    @Test
    fun `check decoding length`() {
        val instructionData = instructionDataBytes.split(", ")
            .map { it.toInt().toByte() }
            .toByteArray()

        val decodedLength = Shortvec.decodeLength(instructionData)
        val expectedDataLength = 368

        Assert.assertEquals(
            "This particular instruction data block should have length 368",
            expectedDataLength,
            decodedLength.first
        )

        val rawTransaction = Base64.getMimeDecoder().decode(encodedBase64Transaction)
        val transaction = Transaction.from(rawTransaction)

        Assert.assertEquals(
            "This transaction should have 3 instructions",
            3,
            transaction.instructions.size
        )
    }
}
