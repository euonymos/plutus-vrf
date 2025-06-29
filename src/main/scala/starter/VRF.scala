package starter

import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script
import scalus.*
import scalus.Compiler.compile
import scalus.builtin.Builtins.{
    blake2b_224,
    bls12_381_G1_hashToGroup,
    bls12_381_finalVerify,
    bls12_381_millerLoop,
    serialiseData
}

import scalus.builtin.Data.{FromData, ToData}
import scalus.builtin.ToData.toData
import scalus.builtin.{ByteString, Data, FromData, ToData}
import scalus.ledger.api.v3.ScriptPurpose.*
import scalus.ledger.api.v3.{TxInfo, TxOutRef}
import scalus.prelude.*
import scalus.prelude.crypto.bls12_381.{G1, G2}
import scalus.sir.SIR
import scalus.uplc.Program

import scala.language.implicitConversions

@Compile
object VRF extends Validator:

    case class VrfDatum(
        // the datum of UTxO contains VRF public key, i.e. g_{2}^{x}, where x - a private key.
        vfrPubKey: ByteString
    )

    case class VrfRedeemer(
        // Signature to prove that the submitter is the holder of a corresponding private key,
        // i.e. H(m)^{x}, where H(m) hash of the VRF input in to G1.
        vrfSignature: ByteString,
        vrfOutput: ByteString
    )

    given FromData[VrfDatum] = FromData.derived[VrfDatum]
    given ToData[VrfDatum] = ToData.derived[VrfDatum]

    given FromData[VrfRedeemer] = FromData.derived[VrfRedeemer]
    given ToData[VrfRedeemer] = ToData.derived[VrfRedeemer]

    override def spend(datum: Option[Data], redeemer: Data, tx: TxInfo, ownRef: TxOutRef): Unit =
        // Extract pub key
        val vrfPubKey = datum.get.to[VrfDatum].vfrPubKey |> G2.uncompress

        // Serialize the spending input utxo id into bytestring
        // and hash it into a group element of G1 - H(m)
        val ownInputBs = tx.inputs.find(i => i.outRef === ownRef).get.outRef
            |> toData
            |> serialiseData
        val ownInputG1 = bls12_381_G1_hashToGroup(ownInputBs, ByteString.fromString("VRF"))

        val signature = redeemer.to[VrfRedeemer].vrfSignature |> G1.uncompress
        // Check the signature e(\signature, g_{2}) = e(H(m), g_{2}^{x}).
        val lhs = bls12_381_millerLoop(signature, G2.generator)
        val rhs = bls12_381_millerLoop(ownInputG1, vrfPubKey)
        val signatureIsValid = bls12_381_finalVerify(lhs, rhs)

        // Check vrfOutput satisfies some condition, i.e.
        // vrfOutput <= threshold
        // or something to that effect.
        val outputIsEligible = true

        require(signatureIsValid && outputIsEligible)
end VRF

object VRFGenerator {
    lazy val sir: SIR = compile(VRF.validate)
    lazy val plutusProgram: Program = sir.toUplcOptimized(generateErrorTraces = true).plutusV3

    lazy val plutusScript: PlutusV3Script = PlutusV3Script
        .builder()
        .`type`("PlutusScriptV3")
        .cborHex(plutusProgram.doubleCborHex)
        .build()
        .asInstanceOf[PlutusV3Script]

    lazy val scriptHash: ByteString = ByteString.fromArray(plutusScript.getScriptHash)
}
