package typedmagic.tavolo.model

import akka.actor.typed.ActorRef
import zio.{Task, ZIO}

case class Participant(
  id: Long,
  push: ActorRef[EstimationSession],
  moniker: String,
  canVote: Boolean,
  currentVote: Option[String],
) {
  def asJson: String =
    s"""
       |{
       |  "id": $id,
       |  "canVote": $canVote,
       |${currentVote.map(v => s"""  "currentVote": "$v",""").getOrElse("")}
       |  "moniker": "$moniker"
       |}
       |""".stripMargin
}

case class EstimationSession private(
  ownerId: Long,
  currentIssue: String,
  deck: Seq[String],
  participants: Seq[Participant],
) {
  def changeOwner(id: Long): EstimationSession = copy(ownerId = id)
  def pushAll: Task[Unit] = ZIO.effect(participants foreach { _.push ! this })
  def +(p: Participant): EstimationSession = copy(participants = this.participants.filterNot(_.id == p.id) :+ p)
  def -(p: Participant): EstimationSession = copy(participants = this.participants.filterNot(_.id == p.id))
  def -(id: Long): EstimationSession = copy(participants = this.participants.filterNot(_.id == id))
  def addVote(id: Long, v: Option[String]): EstimationSession = copy(participants = this.participants.map(p => if (p.id == id) p.copy(currentVote = v) else p))
  def clearVotes(): EstimationSession = copy(participants = this.participants.map(p => p.copy(currentVote = None)))
  def setIssue(s: String): EstimationSession = copy(currentIssue = s)
  def setDeck(d: Seq[String]): EstimationSession = copy(deck = d)

  def asJson: String =
    s"""
       |{
       |  "ownerId": $ownerId,
       |  "currentIssue": "$currentIssue",
       |  "deck": [${deck.map(c => s""""$c"""").mkString(",")}],
       |  "participants": [
       |  ${participants.map(_.asJson).mkString(",")}
       |  ]
       |}
       |""".stripMargin
}

object EstimationSession {
  def apply(owner: Participant): EstimationSession = new EstimationSession(owner.id, "", EstimationDeck.FibPoints, Seq(owner))
}

object EstimationDeck {
  val FibPoints = Seq("0", "½", "1", "2", "3", "5", "8", "13", "20", "40", "100", "∞", "?", "☕")
  val TShirtSizes = Seq("-", "S", "M", "L", "XL", "XXL", "XXXl", "∞", "?", "☕")
  val Letters = Seq("A", "B", "C", "D", "E", "F", "G", "H", "∞", "?", "☕")
  val UpDown = Seq("👍", "👎")
  val Confidence = Seq("✊", "☝️", "✌️", "🤟", "✌️✌️", "🖐")
  private val decks = Array(FibPoints, TShirtSizes, Letters, UpDown, Confidence)
  def apply(i: Int): Seq[String] = decks(i)
}