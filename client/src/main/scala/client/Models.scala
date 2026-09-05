package client

import com.raquo.airstream.state.{StrictSignal, Var}
import ru.trett.rss.models.*
import io.circe.Decoder
import io.circe.generic.semiauto.*

type ChannelList = List[ChannelData]
type FeedItemList = List[FeedItemData]

/** Which subset of the feed list the reader is currently showing. */
enum FeedFilter:
    case AllFeeds
    case Important
    case Channel(id: Long, title: String)

object Decoders:
    given Decoder[UserSettings] = deriveDecoder

final class Model:
    val feedVar: Var[FeedItemList] = Var(List())
    val channelVar: Var[ChannelList] = Var(List())
    val settingsVar: Var[Option[UserSettings]] = Var(Option.empty)
    val unreadCountVar: Var[Int] = Var(0)
    val hasMoreVar: Var[Boolean] = Var(true)
    val feedFilterVar: Var[FeedFilter] = Var(FeedFilter.AllFeeds)
    val sidebarQueryVar: Var[String] = Var("")
    val sidebarOpenVar: Var[Boolean] = Var(false)
    val feedSignal: StrictSignal[FeedItemList] = feedVar.signal
    val channelSignal: StrictSignal[ChannelList] = channelVar.signal
    val settingsSignal: StrictSignal[Option[UserSettings]] = settingsVar.signal
    val unreadCountSignal: StrictSignal[Int] = unreadCountVar.signal
    val hasMoreSignal: StrictSignal[Boolean] = hasMoreVar.signal
    val feedFilterSignal: StrictSignal[FeedFilter] = feedFilterVar.signal
    val sidebarQuerySignal: StrictSignal[String] = sidebarQueryVar.signal
    val sidebarOpenSignal: StrictSignal[Boolean] = sidebarOpenVar.signal
