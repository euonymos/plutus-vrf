package starter

import com.bloxbean.cardano.client.account.Account
import scalus.*
import scalus.builtin.Builtins.{blake2b_224, bls12_381_G1_hashToGroup, serialiseData}
import scalus.builtin.Data.toData
import scalus.builtin.{BLS12_381_G2_Element, ByteString, Data}
import scalus.ledger.api.v2.OutputDatum.OutputDatum
import scalus.ledger.api.v3.*
import scalus.prelude.*
import scalus.prelude.Option.{Some, None}
import scalus.prelude.crypto.bls12_381.G1.{compress as G1compress, scale as G1scale}
import scalus.prelude.crypto.bls12_381.G2
import scalus.prelude.crypto.bls12_381.G2.{compress as G2compress, scale as G2scale}
import scalus.testkit.ScalusTest
import scalus.uplc.Program
import scalus.uplc.eval.{ExBudget, Result}
import starter.Expected.Success
import starter.VRF.{VrfDatum, VrfRedeemer}

import scala.language.implicitConversions
import scala.util.Random

enum Expected {
    case Success(budget: ExBudget)
    case Failure(reason: String)
}

class VRFSpec extends munit.ScalaCheckSuite, ScalusTest {

    private val account = new Account()

    test(s"validator size is ${VRFGenerator.plutusProgram.flatEncoded.length} bytes") {
        val size = VRFGenerator.plutusProgram.flatEncoded.length
        assertEquals(size, 3606)
    }

    def setup(): (BigInt, BLS12_381_G2_Element) =

        val privateKey = Random(System.nanoTime()).alphanumeric
            .take(30)
            .map(_.toByte)
            .toArray
            |> BigInt.apply

        val publicKey = G2.generator.G2scale(privateKey)

        (privateKey, publicKey)

    test(
      "private key owner can prove the random value"
    ) {
        val (privateKey, publicKey) = setup()
        println(s"private key, a random scalar x: $privateKey")
        println(s"public key, g_2 ^ x: $publicKey")

        // Arbitrary input to the VRF, say, a utxo id
        val utxoId = TxOutRef
            .apply(
              TxId.apply(
                ByteString.fromHex(
                  "c2feaec7b53f53328090ebb6ecda11773ed96bdfadb10ab0424fed75786a4333"
                )
              ),
              BigInt(0)
            )
        val message = utxoId.toData |> serialiseData

        // Building the signature and output value based on it
        val hashToG1 = bls12_381_G1_hashToGroup(message, ByteString.fromString("VRF"))
        val signature = hashToG1.G1scale(privateKey)
        println(s"signature: $signature")

        // Output is unique since we hash the signature which in its turn
        // based on the private key
        val vrfOutput = blake2b_224(signature.G1compress)
        println(s"VRF output: $vrfOutput")

        // Make the context and run the script
        val datum = VrfDatum(publicKey.G2compress)
        val redeemer = VrfRedeemer(signature.G1compress, vrfOutput)
        val ctx = makeScriptContext(utxoId, datum, redeemer)

        // run the minting policy script as a Scala function
        VRF.validate(ctx.toData)

//        // run the minting policy script as a Plutus script
//        assertEval(
//          VRFGenerator.plutusProgram $ ctx.toData,
//          Success(ExBudget.fromCpuAndMemory(cpu = 51789034, memory = 200843))
//        )
    }

    private def makeScriptContext(
        utxoId: TxOutRef,
        datum: VrfDatum,
        redeemer: VrfRedeemer
    ): ScriptContext =
        val txOut = random[TxOut] //.apply(random[Address], random[Value], OutputDatum(datum.toData))
        val txInInfo = TxInInfo.apply(utxoId, txOut)
        ScriptContext(
          txInfo = TxInfo(
            inputs = List.Cons(txInInfo, List.Nil),
            fee = BigInt("188021"),
            id = random[TxId]
          ),
          redeemer = redeemer.toData,
          scriptInfo = ScriptInfo.SpendingScript(utxoId, Some(datum.toData))
        )

    private def assertEval(p: Program, expected: Expected): Unit = {
        val result = p.evaluateDebug
        (result, expected) match
            case (result: Result.Success, Expected.Success(expected)) =>
                assertEquals(result.budget, expected)
            case (result: Result.Failure, Expected.Failure(expected)) =>
                assertEquals(result.exception.getMessage, expected)
            case _ => fail(s"Unexpected result: $result, expected: $expected")
    }
}
