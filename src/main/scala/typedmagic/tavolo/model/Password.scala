package typedmagic.tavolo.model

import java.security.MessageDigest

class Password private(val value: String) extends AnyVal

object Password {
  private val AlgoNamePrefix = "{sha1}"
  private lazy val md = MessageDigest.getInstance("SHA1")
  def hash(s: String): String = md.digest(s.getBytes).map(b => String.format("%02x", b)).mkString

  def apply(s: String): Password =  if (s.startsWith(AlgoNamePrefix)) new Password(s) else new Password(AlgoNamePrefix + hash(s))
}
