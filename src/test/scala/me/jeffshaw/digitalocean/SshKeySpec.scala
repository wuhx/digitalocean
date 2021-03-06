package me.jeffshaw.digitalocean

import java.io.{DataOutputStream, ByteArrayOutputStream}
import java.security.interfaces.RSAPublicKey
import java.security.KeyPairGenerator
import java.util.Base64

import org.scalatest.BeforeAndAfterAll

import scala.concurrent._, duration._
import scala.util.Random

/**
 * Note that these tests randomly fail, because Digital Ocean is slow
 * to update your list of keys that are accessible to the API. This is the
 * reason for all the calls to Thread.sleep(int).
 * Sometimes even a 30 second wait isn't enough.
 */
class SshKeySpec extends Spec with BeforeAndAfterAll {
  private def genPK = {
    val kpg = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(512)
    val pc = kpg.genKeyPair.getPublic.asInstanceOf[RSAPublicKey]

    val bytes = new ByteArrayOutputStream
    val out = new DataOutputStream(bytes)
    out.writeInt("ssh-rsa".getBytes.length)
    out.write("ssh-rsa".getBytes)
    out.writeInt(pc.getPublicExponent.toByteArray.length)
    out.write(pc.getPublicExponent.toByteArray)
    out.writeInt(pc.getModulus.toByteArray.length)
    out.write(pc.getModulus.toByteArray)
    val key_val = Base64.getEncoder().encodeToString(bytes.toByteArray)
    s"ssh-rsa $key_val Test Ssh Key"
  }

  private def keysCompare(k1: SshKey, k2: SshKey) = (k1.id == k2.id) && (k1.fingerprint == k2.fingerprint) && (k1.publicKey == k2.publicKey)

  val namePrefix = "ScalaTest"

  private val publicKey: String = genPK

  override protected def afterAll(): Unit = {
    val cleanup = for {
      keys <- SshKey.list
      deletions <- Future.sequence(keys.filter(_.name.startsWith(namePrefix)).map(_.delete))
    } yield deletions

    Await.result(cleanup, 10 seconds)
  }

  test("(randomly fails)Ssh keys can be created, renamed, listed, and deleted (by Id).") {
    val name = namePrefix + Random.nextInt()
    val updatedName = name + "Updated"

    val t = for {
      key <- SshKey.create(name, publicKey)
      () = Thread.sleep(10000)
      keys <- SshKey.list
      () = assert(keys.contains(key))
      newKey <- SshKey.setNameById(key.id, updatedName)
      () = Thread.sleep(60000)
      keysWithRename <- SshKey.list
      () = assert(keysWithRename.contains(newKey))
      () = assert(!keysWithRename.contains(key))
      () = assert(keysCompare(key, newKey))
      () <- SshKey.deleteById(key.id)
      keysAfterDelete <- SshKey.list
      () = assert(!keysAfterDelete.contains(newKey))
    } yield ()

    Await.result(t, 2 minutes)
  }

  test("(randomly fails)Ssh keys can be created, renamed, listed, and deleted (by fingerprint).") {
    val name = namePrefix + Random.nextInt()
    val updatedName = name + "Updated"

    val t = for {
      key <- SshKey.create(name, publicKey)
      () = Thread.sleep(10000)
      keys <- SshKey.list
      () = assert(keys.contains(key))
      newKey <- SshKey.setNameByFingerprint(key.fingerprint, updatedName)
      () = Thread.sleep(60000)
      keysWithRename <- SshKey.list
      () = assert(keysWithRename.contains(newKey))
      () = assert(!keysWithRename.contains(key))
      () = assert(keysCompare(key, newKey))
      () <- SshKey.deleteById(key.id)
      keysAfterDelete <- SshKey.list
      () = assert(!keysAfterDelete.contains(newKey))
    } yield ()

    Await.result(t, 2 minutes)
  }

  test("(randomly fails)Ssh keys can be created, renamed, and deleted (native).") {
    val name = namePrefix + Random.nextInt()
    val updatedName = name + "Updated"

    val t = for {
      key <- SshKey.create(name, publicKey)
      () = Thread.sleep(10000)
      keys <- SshKey.list
      () = assert(keys.contains(key))
      newKey <- key.setName(updatedName)
      () = Thread.sleep(60000)
      keysWithRename <- SshKey.list
      () = assert(keysWithRename.contains(newKey))
      () = assert(!keysWithRename.contains(key))
      () = assert(keysCompare(key, newKey))
      () <- SshKey.deleteById(key.id)
      keysAfterDelete <- SshKey.list
      () = assert(!keysAfterDelete.contains(newKey))
    } yield ()

    Await.result(t, 2 minutes)
  }
}
