package client

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.{StrictSignal, Var}
import ru.trett.rss.models.*
import io.circe.Decoder
import io.circe.generic.semiauto.*

type ChannelList = List[ChannelData]
type FeedItemList = List[FeedItemData]

/** Which of the three panes is on screen. Only meaningful on narrow viewports — on desktop all
  * three are visible at once and this is ignored.
  */
enum Pane:
    case Feeds, List, Article

/** What the list pane is showing. Set by the sidebar, applied by [[Model.visibleFeedSignal]].
  *
  * `Starred` is per-channel, not per-item: it keeps the items whose channel the user highlighted.
  * [[FeedItemData.highlighted]] already carries `user_channels.highlighted` from the server, so no
  * extra request is needed to resolve it.
  */
enum Scope:
    case Unread
    case AllItems
    case Starred
    case Channel(id: Long)

    /** Whether the server has to send read items too. Only `AllItems` changes what is fetched; the
      * rest narrow the set already loaded.
      */
    def includesRead: Boolean = this == Scope.AllItems

object Decoders:
    given Decoder[UserSettings] = deriveDecoder
    given Decoder[ChannelData] = deriveDecoder
    given Decoder[FeedItemData] = deriveDecoder

final class Model:
    val feedVar: Var[FeedItemList] = Var(List())
    val channelVar: Var[ChannelList] = Var(List())
    val settingsVar: Var[Option[UserSettings]] = Var(Option.empty)
    val unreadCountVar: Var[Int] = Var(0)

    /** Read and unread together, for the "All items" row. */
    val totalCountVar: Var[Int] = Var(0)
    val hasMoreVar: Var[Boolean] = Var(true)

    /** Unread count per channel id, for the sidebar. */
    val unreadByChannelVar: Var[Map[Long, Int]] = Var(Map.empty)

    /** Selected article, by feed link. */
    val selectedVar: Var[Option[String]] = Var(Option.empty)

    /** Sidebar selection. */
    val scopeVar: Var[Scope] = Var(Scope.Unread)

    /** Visible pane on narrow viewports. */
    val paneVar: Var[Pane] = Var(Pane.List)

    val feedSignal: StrictSignal[FeedItemList] = feedVar.signal
    val channelSignal: StrictSignal[ChannelList] = channelVar.signal
    val settingsSignal: StrictSignal[Option[UserSettings]] = settingsVar.signal
    val unreadCountSignal: StrictSignal[Int] = unreadCountVar.signal
    val totalCountSignal: StrictSignal[Int] = totalCountVar.signal
    val hasMoreSignal: StrictSignal[Boolean] = hasMoreVar.signal
    val unreadByChannelSignal: StrictSignal[Map[Long, Int]] = unreadByChannelVar.signal
    val scopeSignal: StrictSignal[Scope] = scopeVar.signal
    val paneSignal: StrictSignal[Pane] = paneVar.signal

    /** Items shown in the list pane, narrowed by the current [[Scope]]. A single channel is matched
      * by title because [[FeedItemData]] carries `channelTitle`, not a channel id.
      *
      * `hideRead` is applied here rather than on the server: what a view *contains* is the scope's
      * business, and the server answers that. What the setting decides is narrower — whether a row
      * disappears the moment you read it, or stays dimmed in place until you switch views. Only
      * items read during this session can be affected, since every view except "All items" is
      * fetched unread-only.
      *
      * The row being read is always kept. Dropping it would empty the reading pane and jump the
      * selection elsewhere at the exact moment you opened it.
      */
    val visibleFeedSignal: Signal[FeedItemList] =
        feedSignal
            .combineWith(scopeSignal, channelSignal, settingsSignal, selectedVar.signal)
            .map { case (feeds, scope, channels, settings, selected) =>
                val scoped = scope match
                    case Scope.Unread | Scope.AllItems => feeds
                    case Scope.Starred                 => feeds.filter(_.highlighted)
                    case Scope.Channel(id) =>
                        channels
                            .find(_.id == id)
                            .fold(feeds)(ch => feeds.filter(_.channelTitle == ch.title))
                if scope == Scope.AllItems || !settings.exists(_.hideRead) then scoped
                else scoped.filter(item => !item.isRead || selected.contains(item.link))
            }

    /** The selected item, falling back to the first visible one so the pane is never empty. */
    val selectedItemSignal: Signal[Option[FeedItemData]] =
        visibleFeedSignal.combineWith(selectedVar.signal).map { case (feeds, sel) =>
            sel.flatMap(link => feeds.find(_.link == link)).orElse(feeds.headOption)
        }

    /** Just the selected link. Everything that only cares *which* item is open should watch this
      * rather than [[selectedItemSignal]], which re-emits whenever the feed or channel list changes
      * — including when the open item is merely marked read.
      */
    val selectedLinkSignal: Signal[Option[String]] =
        selectedItemSignal.map(_.map(_.link)).distinct
end Model
