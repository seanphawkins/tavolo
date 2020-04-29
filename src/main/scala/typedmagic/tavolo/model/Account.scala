package typedmagic.tavolo.model

import java.time.Instant

case class Account(
  id: Long = -1,
  email: EmailAddress,
  password: Password,
  moniker: String,
  status: AccountStatus,
  created: Instant = Instant.now(),
  updated: Instant = Instant.now(),
)
