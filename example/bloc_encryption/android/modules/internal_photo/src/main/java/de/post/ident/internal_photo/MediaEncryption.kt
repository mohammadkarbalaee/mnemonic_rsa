package de.post.ident.internal_photo

import android.content.Context
import android.util.Base64
import androidx.core.content.ContextCompat
import de.post.ident.internal_core.CoreConfig
import de.post.ident.internal_core.util.log
import org.spongycastle.crypto.AsymmetricBlockCipher
import org.spongycastle.crypto.encodings.OAEPEncoding
import org.spongycastle.crypto.engines.RSAEngine
import org.spongycastle.crypto.params.AsymmetricKeyParameter
import org.spongycastle.crypto.util.PublicKeyFactory
import java.io.*
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

object MediaEncryption {

    fun encryptMediaFile(inputFile: File) {
        val outputFile: File = createFileForEncryption(inputFile)
        val secretKey: Key = generateAESKey(128)
        val randomBytes: ByteArray = generateRandomByteArray(16)

        encryptFile(secretKey, randomBytes, inputFile, outputFile)
        createFileForEncryptionParameter(inputFile).writeBytes(randomBytes)
        encryptKey(secretKey, createEncryptedKeyFile(inputFile))
    }

    fun clearStepFiles(context: Context, caseId: String, filename: String) {
        File(getMediaPath(context, caseId)).listFiles()?.filter {
            it.name.contains(filename)
        }?.map {
            it.delete()
        }
    }

    private fun createEncryptedKeyFile(inputFile: File): File {
        val fileSuffix: String = inputFile.extension
        val newName: String = inputFile.absolutePath.replace(".$fileSuffix", "_txt.bin")
        val encryptedFile = File(newName)

        log("Encrypting key for file: ${inputFile.name}")
        log("Created encrypted key file: ${encryptedFile.name}")

        return encryptedFile
    }

    private fun encryptKey(secretKey: Key, encryptedKeyOutputFile: File) {
        val publicKeyRSA: PublicKey = transformToRSAPublicKey(decodeBase64(CoreConfig.getRsaKey()))
        val encryptedBytes: ByteArray = encryptWithOAEPPadding(secretKey.encoded, publicKeyRSA)
        encryptedKeyOutputFile.writeBytes(encryptedBytes)
    }

    private fun encryptWithOAEPPadding(plainToEncrypt: ByteArray, rsaKey: Key): ByteArray {
        val privateKey: AsymmetricKeyParameter? = PublicKeyFactory.createKey(rsaKey.encoded)
        var cipher: AsymmetricBlockCipher = RSAEngine()
        cipher = OAEPEncoding(cipher)
        cipher.init(true, privateKey)
        return cipher.processBlock(plainToEncrypt, 0, plainToEncrypt.size)
    }

    private fun decodeBase64(value: String): ByteArray {
        return Base64.decode(value.toByteArray(), Base64.NO_WRAP)
    }

    private fun transformToRSAPublicKey(keyBytes: ByteArray): PublicKey {
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }

    private fun createFileForEncryptionParameter(inputFile: File): File {

        val fileSuffix: String = inputFile.extension
        val newName: String = inputFile.absolutePath.replace(".$fileSuffix", "_iv.bin")
        return File(newName)
    }

    private fun encryptFile(secretKey: Key, randomBytes: ByteArray, inputFile: File, outputFile: File) {
        inputFile.inputStream().use { inputStream ->
            outputFile.outputStream().use { outputStream ->
                CipherOutputStream(outputStream, createCipher(secretKey, randomBytes)).use { cipher ->
                    inputStream.copyTo(cipher, 1024)
                }
            }
        }
    }

    private fun createCipher(key: Key, randomBytes: ByteArray): Cipher {
        val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        aesCipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(randomBytes))
        return aesCipher
    }

    private fun generateRandomByteArray(size: Int): ByteArray {
        val randomArray = ByteArray(size)
        val random = Random()
        random.nextBytes(randomArray)

        return randomArray
    }

    private fun generateAESKey(keySize: Int): Key {
        val kgen = KeyGenerator.getInstance("AES")
        kgen.init(keySize, SecureRandom())
        return kgen.generateKey()
    }

    private fun createFileForEncryption(inputFile: File): File {
        val suffix: String = inputFile.extension
        val newName: String = inputFile.absolutePath.replace(".$suffix", "_$suffix.bin")
        val encryptedFile = File(newName)
        log("Encrypting file: $inputFile")
        log("Created encrypted file: ${encryptedFile.name}")

        return encryptedFile
    }

    fun getMediaPath(context: Context, caseId: String): String = File("${getRootPath(context, caseId)}/media").apply { mkdir() }.absolutePath

    fun getRootPath(context: Context, caseId: String): String = File("${ContextCompat.getNoBackupFilesDir(context)}/${caseId}").apply { mkdir() }.absolutePath
}

val File.extension: String get() = name.substringAfterLast('.', "")