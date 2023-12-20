package com.bloxbean.cardano.client.transaction;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.KeyGenUtil;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyGenerator;
import com.bloxbean.cardano.client.crypto.bip32.HdKeyPair;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import com.bloxbean.cardano.client.exception.AddressExcepion;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.VkeyWitness;

import java.util.ArrayList;

import static com.bloxbean.cardano.client.transaction.util.TransactionUtil.createCopy;

public enum TransactionSigner {
    INSTANCE();

    TransactionSigner() {

    }

    public Transaction sign(Transaction transaction, HdKeyPair hdKeyPair) {
        return sign(transaction, hdKeyPair, true);
    }

    public Transaction sign(Transaction transaction, HdKeyPair hdKeyPair, boolean safe) {

        Transaction cloneTxn;
        if (safe) {
            cloneTxn = createCopy(transaction);
        } else {
            cloneTxn = transaction;
        }
        TransactionBody transactionBody = cloneTxn.getBody();

        byte[] txnBody;
        try {
            txnBody = CborSerializationUtil.serialize(transactionBody.serialize());
        } catch (CborException | AddressExcepion | CborSerializationException e) {
            throw new CborRuntimeException("Error in Cbor serialization", e);
        }

        byte[] txnBodyHash = Blake2bUtil.blake2bHash256(txnBody);

        SigningProvider signingProvider = CryptoConfiguration.INSTANCE.getSigningProvider();
        byte[] signature = signingProvider.signExtended(txnBodyHash, hdKeyPair.getPrivateKey().getKeyData(), hdKeyPair.getPublicKey().getKeyData());

        VkeyWitness vkeyWitness = VkeyWitness.builder()
                .vkey(hdKeyPair.getPublicKey().getKeyData())
                .signature(signature)
                .build();

        if (cloneTxn.getWitnessSet() == null)
            cloneTxn.setWitnessSet(new TransactionWitnessSet());

        if (cloneTxn.getWitnessSet().getVkeyWitnesses() == null)
            cloneTxn.getWitnessSet().setVkeyWitnesses(new ArrayList<>());

        cloneTxn.getWitnessSet().getVkeyWitnesses().add(vkeyWitness);

        return cloneTxn;
    }

    public Transaction sign(Transaction transaction, SecretKey secretKey) {
        return this.sign(transaction, secretKey, true);
    }

    public Transaction sign(Transaction transaction, SecretKey secretKey, boolean safe) {
        Transaction cloneTxn;
        if (safe) {
            cloneTxn = createCopy(transaction);
        } else {
            cloneTxn = transaction;
        }
        TransactionBody transactionBody = cloneTxn.getBody();

        byte[] txnBody;
        try {
            txnBody = CborSerializationUtil.serialize(transactionBody.serialize());
        } catch (CborException | AddressExcepion | CborSerializationException e) {
            throw new CborRuntimeException("Error in Cbor serialization", e);
        }

        byte[] txnBodyHash = Blake2bUtil.blake2bHash256(txnBody);

        SigningProvider signingProvider = CryptoConfiguration.INSTANCE.getSigningProvider();
        VerificationKey verificationKey;
        byte[] signature;

        if (secretKey.getBytes().length == 64) { //extended pvt key (most prob for regular account)
            //check for public key
            byte[] vBytes = HdKeyGenerator.getPublicKey(secretKey.getBytes());
            signature = signingProvider.signExtended(txnBodyHash, secretKey.getBytes(), vBytes);

            try {
                verificationKey = VerificationKey.create(vBytes);
            } catch (CborSerializationException e) {
                throw new CborRuntimeException("Unable to get verification key from secret key", e);
            }
        } else {
            signature = signingProvider.sign(txnBodyHash, secretKey.getBytes());
            try {
                verificationKey = KeyGenUtil.getPublicKeyFromPrivateKey(secretKey);
            } catch (CborSerializationException e) {
                throw new CborRuntimeException("Unable to get verification key from SecretKey", e);
            }
        }

        VkeyWitness vkeyWitness = VkeyWitness.builder()
                .vkey(verificationKey.getBytes())
                .signature(signature)
                .build();

        if (cloneTxn.getWitnessSet() == null)
            cloneTxn.setWitnessSet(new TransactionWitnessSet());

        if (cloneTxn.getWitnessSet().getVkeyWitnesses() == null)
            cloneTxn.getWitnessSet().setVkeyWitnesses(new ArrayList<>());

        cloneTxn.getWitnessSet().getVkeyWitnesses().add(vkeyWitness);

        return cloneTxn;
    }

}
