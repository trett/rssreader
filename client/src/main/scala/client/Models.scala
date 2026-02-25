package client

import com.raquo.airstream.state.{StrictSignal, Var}
import ru.trett.rss.models.*
import io.circe.Decoder
import io.circe.generic.semiauto.*

type ChannelList = List[ChannelData]
type FeedItemList = List[FeedItemData]

object Decoders:
    given Decoder[UserSettings] = deriveDecoder
    given Decoder[SummarySuccess] = deriveDecoder
    given Decoder[SummaryError] = deriveDecoder
    given Decoder[SummaryResult] = Decoder.instance { cursor =>
        cursor.downField("type").as[String].flatMap {
            case "success" => cursor.as[SummarySuccess]
            case "error"   => cursor.as[SummaryError]
            case other =>
                Left(
                    io.circe.DecodingFailure(s"Unknown SummaryResult type: $other", cursor.history)
                )
        }
    }
    given Decoder[SummaryResponse] = deriveDecoder

    import SummaryEvent.*
    given Decoder[Content] = deriveDecoder
    given Decoder[Metadata] = deriveDecoder
    given Decoder[FunFact] = deriveDecoder
    given Decoder[Error] = deriveDecoder

    given Decoder[SummaryEvent] = Decoder.instance { cursor =>
        cursor.downField("type").as[String].flatMap {
            case "content"  => cursor.as[Content]
            case "metadata" => cursor.as[Metadata]
            case "funFact"  => cursor.as[FunFact]
            case "error"    => cursor.as[Error]
            case "done"     => Right(Done)
            case other =>
                Left(io.circe.DecodingFailure(s"Unknown SummaryEvent type: $other", cursor.history))
        }
    }

final class Model:
    val feedVar: Var[FeedItemList] = Var(List())
    val channelVar: Var[ChannelList] = Var(List())
    val settingsVar: Var[Option[UserSettings]] = Var(Option.empty)
    val unreadCountVar: Var[Int] = Var(0)
    val hasMoreVar: Var[Boolean] = Var(true)
    val feedSignal: StrictSignal[FeedItemList] = feedVar.signal
    val channelSignal: StrictSignal[ChannelList] = channelVar.signal
    val settingsSignal: StrictSignal[Option[UserSettings]] = settingsVar.signal
    val unreadCountSignal: StrictSignal[Int] = unreadCountVar.signal
    val hasMoreSignal: StrictSignal[Boolean] = hasMoreVar.signal
