package typedmagic.tavolo.model

sealed trait AccountStatus { def value: Int }

object AccountStatus {
  case object UNVERIFIED extends AccountStatus { def value = 0 }
  case object ACTIVE extends AccountStatus { def value = 1 }
  case object LOCKED extends AccountStatus { def value = 2 }

  private val instances =
    List(UNVERIFIED, ACTIVE, LOCKED)
      .map(v => (v.value, v))
      .toMap

  def apply(i: Int): AccountStatus = instances(i)
}
