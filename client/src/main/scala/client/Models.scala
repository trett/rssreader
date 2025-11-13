package client

import com.raquo.airstream.state.{StrictSignal, Var}
import ru.trett.rss.models.*

type ChannelList = List[ChannelData]
type FeedItemList = List[FeedItemData]

final class Model:
    val feedVar: Var[FeedItemList] = Var(List())
    val channelVar: Var[ChannelList] = Var(List())
    val settingsVar: Var[Option[UserSettings]] = Var(Option.empty)
    val feedSignal: StrictSignal[FeedItemList] = feedVar.signal
    val channelSignal: StrictSignal[ChannelList] = channelVar.signal
    val settingsSignal: StrictSignal[Option[UserSettings]] = settingsVar.signal
    val unreadCountSignal = feedSignal.map(_.count(!_.isRead))
