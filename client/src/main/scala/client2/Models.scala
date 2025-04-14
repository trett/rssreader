package client2

import com.raquo.airstream.state.Var
import ru.trett.rss.models.*

type ChannelList = List[ChannelData]
type FeedItemList = List[FeedItemData]

final class Model:
    val feedVar: Var[FeedItemList] = Var(List())
    val channelVar: Var[ChannelList] = Var(List())
    val settingsVar: Var[Option[UserSettings]] = Var(Option.empty)
    val feedSignal = feedVar.signal
    val channelSignal = channelVar.signal
    val settingsSignal = settingsVar.signal
