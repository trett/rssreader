package client2

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import java.time.LocalDateTime

case class FeedItemData(
    id: Long,
    title: String,
    channelTitle: String,
    guid: Option[String],
    link: String,
    description: String,
    pubDate: LocalDateTime,
    read: Boolean
)

case class ChannelData(
    id: Long,
    title: String
    // link: String,
    // channelLink: String,
    // feedItems: FeedItemList
)

case class SettingsData(hideRead: Boolean, deleteAfter: Int)

type ChannelList = List[ChannelData]
type FeedItemList = List[FeedItemData]

final class Model:
    val feedVar: Var[FeedItemList] = Var(List())
    val channelVar: Var[ChannelList] = Var(List())
    val settingsVar: Var[Option[SettingsData]] = Var(Option.empty)
    val feedSignal = feedVar.signal
    val channelSignal = channelVar.signal
    val settingsSignal = settingsVar.signal
