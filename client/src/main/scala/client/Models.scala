package client

import com.raquo.airstream.state.{StrictSignal, Var}
import com.raquo.laminar.api.L.*
import ru.trett.rss.models.*
import client.NetworkUtils.responseDecoder
import io.circe.Decoder
import io.circe.generic.semiauto.*
import scala.util.{Try, Success, Failure}

type ChannelList = List[ChannelData]
type FeedItemList = List[FeedItemData]

final class Model:
    given Decoder[UserSettings] = deriveDecoder

    val feedVar: Var[FeedItemList] = Var(List())
    val channelVar: Var[ChannelList] = Var(List())
    val settingsVar: Var[Option[UserSettings]] = Var(Option.empty)
    val unreadCountVar: Var[Int] = Var(0)
    val feedSignal: StrictSignal[FeedItemList] = feedVar.signal
    val channelSignal: StrictSignal[ChannelList] = channelVar.signal
    val settingsSignal: StrictSignal[Option[UserSettings]] = settingsVar.signal
    val unreadCountSignal: StrictSignal[Int] = unreadCountVar.signal

    private val settingsLoadingVar: Var[Boolean] = Var(false)

    // Centralized settings fetch - prevents race conditions
    def ensureSettingsLoaded(): EventStream[Try[Option[UserSettings]]] =
        if (settingsVar.now().isDefined || settingsLoadingVar.now()) then
            EventStream.empty
        else
            settingsLoadingVar.set(true)
            FetchStream
                .withDecoder(responseDecoder[Option[UserSettings]])
                .get("/api/user/settings")
                .map {
                    case Success(Some(value)) => Success(value)
                    case Success(None) => Failure(new RuntimeException("Failed to decode settings response"))
                    case Failure(err) => Failure(err)
                }
                .map { result =>
                    settingsLoadingVar.set(false)
                    result
                }
