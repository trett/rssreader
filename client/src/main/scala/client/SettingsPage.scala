package client

import client.NetworkUtils.*
import client.NotifyComponent.infoMessage
import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.features.unitArrows
import io.circe.Encoder
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import ru.trett.rss.models.*

import scala.util.{Failure, Success, Try}

/** Settings without UI5, inside the reader's own shell.
  *
  * Three things changed structurally, not just visually:
  *
  *   - The page lives under a sidebar instead of being a card on a grey backdrop, so "Back to
  *     feeds" is a nav row rather than a link floating above the content.
  *   - Every control sits next to its label and its one-line explanation, capped at a 700px
  *     measure. The old layout pushed labels and controls to opposite edges of the viewport.
  *   - Saving is explicit and only offered when there is something to save: a sticky bar appears
  *     when the form differs from what the server last confirmed.
  *
  * Per-channel folder, highlight, delete and add keep saving immediately — each has its own
  * endpoint and always did. The sticky bar covers only the `UserSettings` POST, which is the one
  * request that used to need the button.
  */
object SettingsPage:

    private val model = AppState.model
    import model.*
    import Decoders.given
    given Encoder[UserSettings] = deriveEncoder

    /** What the server last confirmed — the baseline the dirty state is measured against. */
    private val savedSettingsVar = Var[Option[UserSettings]](None)
    private val keyVisibleVar = Var(false)
    private val keyEditingVar = Var(false)
    private val keywordInputVar = Var("")
    private val bannedInputVar = Var("")
    private val newChannelVar = Var("")
    private val savedNoteVar = Var(false)

    /** Re-fetch the channel list: after an add, which is the only change that can introduce a row
      * this page did not already know about.
      */
    private val reloadChannelsBus: EventBus[Unit] = new EventBus
    private val addChannelBus: EventBus[String] = new EventBus

    private val dirtySignal: Signal[Boolean] =
        settingsSignal.combineWith(savedSettingsVar.signal).map { case (current, saved) =>
            saved.isDefined && current != saved
        }

    private val settingsObserver = Observer[Try[Option[UserSettings]]] {
        case Success(settings) =>
            settingsVar.set(settings)
            savedSettingsVar.set(settings)
        case Failure(err) => handleError(err)
    }

    private val channelsObserver = Observer[Try[ChannelList]] {
        case Success(channels) => channelVar.set(channels)
        case Failure(err)      => handleError(err)
    }

    private val updateSettingsObserver = Observer[Try[Unit]] {
        case Success(_) =>
            savedSettingsVar.set(settingsSignal.now())
            savedNoteVar.set(true)
            infoMessage("Settings saved")
        case Failure(err) => handleError(err)
    }

    private val deleteChannelObserver = Observer[Try[Long]] {
        case Success(id) =>
            channelVar.update(_.filterNot(_.id == id))
            infoMessage("Feed removed")
        case Failure(err) => handleError(err)
    }

    private val addChannelObserver = Observer[Try[Unit]] {
        case Success(_) =>
            newChannelVar.set("")
            reloadChannelsBus.emit(())
            infoMessage("Feed added")
        case Failure(err) => handleError(err)
    }

    def render: Element =
        div(
            cls := "rr-settings-app",
            onMountBind(_ => getSettingsRequest --> settingsObserver),
            onMountBind(_ =>
                EventStream
                    .merge(EventStream.unit(), reloadChannelsBus.events)
                    .flatMapSwitch(_ => NetworkUtils.getChannels()) --> channelsObserver
            ),
            sidebar,
            div(
                cls := "rr-settings",
                headerTag(
                    cls := "rr-settings-header",
                    h1(cls := "rr-settings-h1", "Settings"),
                    span(cls := "rr-settings-hint", "saved per account")
                ),
                div(
                    cls := "rr-settings-scroll",
                    div(
                        cls := "rr-settings-measure",
                        readingSection,
                        filteringSection,
                        feedsSection,
                        savedNote,
                        saveBar
                    )
                )
            )
        )

    /* ── sidebar ─────────────────────────────────────────────────────────── */

    /** Not [[Sidebar.render]]: that one is the reader's, and its scope rows and per-channel unread
      * counts would fire two requests and then do nothing when clicked from here.
      */
    private def sidebar: Element =
        div(
            cls := "rr-sidebar",
            div(cls := "rr-brand", span(cls := "rr-brand-dot"), span("RSS Reader")),
            div(
                cls := "rr-sidebar-scroll",
                div(
                    cls := "rr-nav",
                    div(
                        cls := "rr-nav-row",
                        tabIndex := 0,
                        span(cls := "rr-nav-back", "‹"),
                        span(cls := "rr-nav-title", "Back to feeds"),
                        onClick --> {
                            if settingsSignal.now().isDefined then Router.toMainPage()
                            else Router.currentPageVar.set(Some(LoginRoute))
                        }
                    ),
                    div(cls := "rr-nav-row is-active", span(cls := "rr-nav-title", "Settings"))
                )
            )
        )

    /* ── reading ─────────────────────────────────────────────────────────── */

    private def readingSection: Element =
        sectionTag(
            cls := "rr-set-group",
            div(cls := "rr-set-legend", "Reading"),
            settingRow(
                "Remove items as you read them",
                "Off leaves them dimmed in place until you switch views.",
                toggle(
                    settingsSignal.map(_.exists(_.hideRead)),
                    v => settingsVar.update(_.map(_.copy(hideRead = v)))
                )
            )
        )

    /* ── importance filtering ────────────────────────────────────────────── */

    private val hasKeySignal: Signal[Boolean] =
        settingsSignal.map(_.flatMap(_.geminiApiKey).exists(_.trim.nonEmpty))

    private def filteringSection: Element =
        sectionTag(
            cls := "rr-set-group",
            div(cls := "rr-set-legend", "Importance filtering"),
            div(
                cls := "rr-set-row has-divider",
                div(
                    cls := "rr-set-label-block",
                    div(
                        cls := "rr-set-label-line",
                        span(cls := "rr-set-label", "Gemini API key"),
                        span(
                            cls := "rr-set-status",
                            cls("is-on") <-- hasKeySignal,
                            span(cls := "rr-status-dot"),
                            child.text <-- hasKeySignal.map(if _ then "Connected" else "Not set")
                        )
                    ),
                    p(
                        cls := "rr-set-help",
                        "Stored on the server against your account. Replacing it overwrites the "
                            + "stored value."
                    ),
                    child <-- keyEditingVar.signal.map(if _ then keyEditor else keyDisplay)
                )
            ),
            div(
                cls := "rr-set-row",
                cls("is-disabled") <-- hasKeySignal.map(!_),
                div(
                    cls := "rr-set-label-block",
                    div(cls := "rr-set-label", "Filter news by importance"),
                    p(
                        cls := "rr-set-help",
                        child.text <-- hasKeySignal.map {
                            case true =>
                                "Scores incoming items against your keyword rules so the "
                                    + "Important view stays short."
                            case false => "Add a Gemini API key above to enable filtering."
                        }
                    )
                ),
                toggle(
                    settingsSignal.combineWith(hasKeySignal).map { case (s, hasKey) =>
                        hasKey && s.exists(_.filterNews)
                    },
                    v => settingsVar.update(_.map(_.copy(filterNews = v))),
                    enabled = hasKeySignal
                )
            ),
            tagSection(
                heading = "Keyword rules",
                help = "An item whose title or category matches is always marked Important.",
                hint = "Add a word, or several separated by commas",
                emptyNote = "No rules — nothing is force-marked Important.",
                chipClass = "is-keyword",
                primaryAdd = true,
                inputVar = keywordInputVar,
                itemsSignal = settingsSignal.map(_.map(_.keywordRules).getOrElse(List.empty)),
                onAdd = raw =>
                    val words = raw.split(",").map(_.trim).filter(_.nonEmpty).toList
                    settingsVar.update(
                        _.map(s => s.copy(keywordRules = (s.keywordRules ++ words).distinct))
                    )
                ,
                onRemove = kw =>
                    settingsVar.update(
                        _.map(s => s.copy(keywordRules = s.keywordRules.filterNot(_ == kw)))
                    )
            ),
            tagSection(
                heading = "Banned categories",
                help = "Items in these categories never reach the Important view, whatever the "
                    + "model thinks.",
                hint = "Add a category",
                emptyNote = "Nothing banned.",
                chipClass = "is-banned",
                primaryAdd = false,
                inputVar = bannedInputVar,
                itemsSignal = settingsSignal.map(_.map(_.bannedCategories).getOrElse(List.empty)),
                onAdd = raw =>
                    val cats = raw.split(",").map(_.trim).filter(_.nonEmpty).toList
                    settingsVar.update(
                        _.map(s => s.copy(bannedCategories = (s.bannedCategories ++ cats).distinct))
                    )
                ,
                onRemove = cat =>
                    settingsVar.update(
                        _.map(s =>
                            s.copy(bannedCategories = s.bannedCategories.filterNot(_ == cat))
                        )
                    )
            )
        )
    end filteringSection

    private def keyDisplay: HtmlElement =
        div(
            cls := "rr-set-control-row",
            div(
                cls := "rr-key-box",
                span(
                    cls := "rr-key-value",
                    cls("is-empty") <-- hasKeySignal.map(!_),
                    child.text <-- settingsSignal
                        .combineWith(keyVisibleVar.signal)
                        .map { case (s, visible) =>
                            s.flatMap(_.geminiApiKey).filter(_.trim.nonEmpty) match
                                case None               => "Not set"
                                case Some(k) if visible => k
                                case Some(k)            => "•" * math.min(k.length, 34)
                        }
                )
            ),
            button(
                cls := "rr-ghost-button",
                tpe := "button",
                child.text <-- keyVisibleVar.signal.map(if _ then "Hide" else "Reveal"),
                disabled <-- hasKeySignal.map(!_),
                onClick --> keyVisibleVar.update(!_)
            ),
            button(
                cls := "rr-ghost-button",
                tpe := "button",
                child.text <-- hasKeySignal.map(if _ then "Replace" else "Add key"),
                onClick --> keyEditingVar.set(true)
            )
        )

    private def keyEditor: HtmlElement =
        div(
            cls := "rr-set-control-row",
            input(
                cls := "rr-input is-mono",
                tpe := "password",
                placeholder := "AIza…",
                autoFocus := true,
                onInput.mapToValue --> { v =>
                    settingsVar.update(
                        _.map(_.copy(geminiApiKey = Option(v.trim).filter(_.nonEmpty)))
                    )
                },
                onKeyDown.filter(_.key == "Enter").preventDefault --> keyEditingVar.set(false)
            ),
            button(
                cls := "rr-ghost-button",
                tpe := "button",
                "Done",
                onClick --> keyEditingVar.set(false)
            )
        )

    private def tagSection(
        heading: String,
        help: String,
        hint: String,
        emptyNote: String,
        chipClass: String,
        primaryAdd: Boolean,
        inputVar: Var[String],
        itemsSignal: Signal[List[String]],
        onAdd: String => Unit,
        onRemove: String => Unit
    ): HtmlElement =
        def commit(): Unit =
            val text = inputVar.now().trim
            if text.nonEmpty then
                onAdd(text)
                inputVar.set("")

        div(
            cls := "rr-set-block",
            div(
                cls := "rr-set-label-block",
                div(cls := "rr-set-label", heading),
                p(cls := "rr-set-help", help)
            ),
            div(
                cls := "rr-set-control-row",
                input(
                    cls := "rr-input",
                    tpe := "text",
                    placeholder := hint,
                    controlled(value <-- inputVar.signal, onInput.mapToValue --> inputVar),
                    onKeyDown.filter(_.key == "Enter").preventDefault --> commit()
                ),
                button(
                    cls := (if primaryAdd then "rr-primary-button" else "rr-ghost-button"),
                    tpe := "button",
                    "Add",
                    onClick --> commit()
                )
            ),
            div(
                cls := "rr-chips",
                children <-- itemsSignal.split(identity) { (item, _, _) =>
                    div(
                        cls := s"rr-chip $chipClass",
                        span(item),
                        span(
                            cls := "rr-chip-x",
                            role := "button",
                            title := s"Remove $item",
                            "×",
                            onClick --> onRemove(item)
                        )
                    )
                },
                div(cls := "rr-chips-empty", hidden <-- itemsSignal.map(_.nonEmpty), emptyNote)
            )
        )
    end tagSection

    /* ── feeds ───────────────────────────────────────────────────────────── */

    private def feedsSection: Element =
        sectionTag(
            cls := "rr-set-group",
            // On the section, not on a row: mounted per row it would fire one POST per existing
            // feed, and none at all while the list is empty.
            addChannelBus.events.flatMapSwitch(addChannelRequest) --> addChannelObserver,
            div(
                cls := "rr-set-legend-row",
                div(cls := "rr-set-legend", "Feeds"),
                div(
                    cls := "rr-set-legend-meta",
                    child.text <-- channelSignal.map { chs =>
                        s"${chs.size} feeds · ${chs.count(_.highlighted)} highlighted"
                    }
                )
            ),
            div(
                cls := "rr-table-head",
                span("Feed"),
                span("Folder"),
                span(cls := "rr-center", "★"),
                span()
            ),
            div(children <-- channelSignal.split(_.id)(renderChannel)),
            div(
                cls := "rr-set-control-row rr-add-feed",
                input(
                    cls := "rr-input is-mono",
                    tpe := "url",
                    placeholder := "https://example.com/feed.xml",
                    controlled(
                        value <-- newChannelVar.signal,
                        onInput.mapToValue --> newChannelVar
                    ),
                    onKeyDown.filter(_.key == "Enter").preventDefault --> addChannel()
                ),
                button(
                    cls := "rr-primary-button",
                    tpe := "button",
                    "Add feed",
                    disabled <-- newChannelVar.signal.map(_.trim.isEmpty),
                    onClick --> addChannel()
                )
            )
        )

    private def addChannel(): Unit =
        val url = newChannelVar.now().trim
        if url.nonEmpty then addChannelBus.emit(url)

    private def renderChannel(
        id: Long,
        item: ChannelData,
        itemSignal: Signal[ChannelData]
    ): HtmlElement =
        div(
            cls := "rr-table-row",
            div(
                cls := "rr-table-feed",
                span(
                    cls := "rr-dot",
                    styleAttr <-- itemSignal.map(ch => s"background:${FeedColors.of(ch.title)}")
                ),
                span(cls := "rr-table-feed-name", child.text <-- itemSignal.map(_.title))
            ),
            input(
                cls := "rr-cell-input",
                tpe := "text",
                placeholder := "Ungrouped",
                title := "Folder — groups the feed in the sidebar. Empty means ungrouped.",
                value <-- itemSignal.map(_.folder.getOrElse("")),
                onChange.mapToValue.flatMap(updateChannelFolderRequest(id, _)) -->
                    Observer[Try[Option[String]]] {
                        case Success(folder) =>
                            channelVar.update(
                                _.map(ch => if ch.id == id then ch.copy(folder = folder) else ch)
                            )
                            infoMessage("Folder updated")
                        case Failure(err) => handleError(err)
                    }
            ),
            button(
                cls := "rr-star",
                tpe := "button",
                cls("is-on") <-- itemSignal.map(_.highlighted),
                title := "Highlight this feed",
                child.text <-- itemSignal.map(ch => if ch.highlighted then "★" else "☆"),
                onClick.compose(
                    _.sample(itemSignal)
                        .flatMapSwitch(ch => updateChannelHighlightRequest(id, !ch.highlighted))
                ) --> Observer[Try[Boolean]] {
                    case Success(value) =>
                        channelVar.update(
                            _.map(ch => if ch.id == id then ch.copy(highlighted = value) else ch)
                        )
                    case Failure(err) => handleError(err)
                }
            ),
            button(
                cls := "rr-remove",
                tpe := "button",
                title := "Remove this feed",
                "×",
                onClick.flatMap(_ => deleteChannelRequest(id)) --> deleteChannelObserver
            )
        )
    end renderChannel

    /* ── save bar ────────────────────────────────────────────────────────── */

    /** Ahead of the bar, never inside a shared wrapper: the bar's negative bottom margin pulls
      * whatever follows it up and over its own buttons, and an `opacity: 0` note still takes
      * clicks.
      */
    private def savedNote: Element =
        div(
            cls := "rr-saved-note",
            cls("is-visible") <-- savedNoteVar.signal
                .combineWith(dirtySignal)
                .map { case (saved, dirty) => saved && !dirty },
            "All changes saved"
        )

    /** A direct child of the measure, not of a wrapper: `position: sticky` can only travel inside
      * its containing block, so a wrapper sized to the bar itself would pin it in place.
      */
    private def saveBar: Element =
        div(
            cls := "rr-save-bar",
            cls("is-visible") <-- dirtySignal,
            dirtySignal.changes.filter(identity) --> Observer[Boolean](_ =>
                savedNoteVar.set(false)
            ),
            span(cls := "rr-save-note", "Unsaved changes"),
            div(
                cls := "rr-save-actions",
                button(
                    cls := "rr-ghost-button",
                    tpe := "button",
                    "Discard",
                    onClick --> {
                        settingsVar.set(savedSettingsVar.now())
                        keyEditingVar.set(false)
                    }
                ),
                button(
                    cls := "rr-primary-button",
                    tpe := "button",
                    "Save changes",
                    onClick.compose(
                        _.sample(settingsSignal).flatMapSwitch(updateSettingsRequest)
                    ) --> updateSettingsObserver
                )
            )
        )

    /* ── requests (unchanged endpoints) ──────────────────────────────────── */

    private def getSettingsRequest: EventStream[Try[Option[UserSettings]]] =
        FetchStream
            .withDecoder(responseDecoder[Option[UserSettings]])
            .get("/api/user/settings")
            .mapSuccess(_.flatten)

    private def updateSettingsRequest(settings: Option[UserSettings]): EventStream[Try[Unit]] =
        settings match
            case Some(s) =>
                FetchStream
                    .withDecoder(responseDecoder[String])
                    .post(
                        "/api/user/settings",
                        _.body(s.asJson.toString),
                        _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
                    )
                    .mapSuccess(_ => ())
            case None => EventStream.empty

    private def addChannelRequest(link: String): EventStream[Try[Unit]] =
        FetchStream
            .withDecoder(responseDecoder[String])
            .post(
                "/api/channels",
                _.body(link.asJson.toString),
                _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
            )
            .mapSuccess(_ => ())

    private def deleteChannelRequest(id: Long): EventStream[Try[Long]] =
        FetchStream
            .withDecoder(responseDecoder[Long])
            .apply(_.DELETE, s"/api/channels/$id")
            .mapSuccess(_.getOrElse(id))

    private def updateChannelHighlightRequest(
        id: Long,
        highlighted: Boolean
    ): EventStream[Try[Boolean]] =
        FetchStream
            .withDecoder(responseDecoder[String])
            .put(
                s"/api/channels/$id/highlight",
                _.body(highlighted.asJson.toString),
                _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
            )
            .mapSuccess(_ => highlighted)

    /** Blank clears the folder. The server normalises too, but doing it here keeps the optimistic
      * update in step with what was actually stored.
      */
    private def updateChannelFolderRequest(
        id: Long,
        folder: String
    ): EventStream[Try[Option[String]]] =
        val normalized = Option(folder.trim).filter(_.nonEmpty)
        FetchStream
            .withDecoder(responseDecoder[String])
            .put(
                s"/api/channels/$id/folder",
                _.body(normalized.asJson.toString),
                _.headers(JSON_ACCEPT, JSON_CONTENT_TYPE)
            )
            .mapSuccess(_ => normalized)

    /* ── small building blocks ───────────────────────────────────────────── */

    private def settingRow(label: String, help: String, control: HtmlElement): HtmlElement =
        div(
            cls := "rr-set-row",
            div(
                cls := "rr-set-label-block",
                div(cls := "rr-set-label", label),
                p(cls := "rr-set-help", help)
            ),
            control
        )

    /** `enabled` is sampled at click time rather than read from a separately observed signal, so
      * the switch cannot leak an observation per interaction.
      */
    private def toggle(
        checked: Signal[Boolean],
        onToggle: Boolean => Unit,
        enabled: Signal[Boolean] = Signal.fromValue(true)
    ): HtmlElement =
        val state = checked.combineWith(enabled)
        val flip = Observer[(Boolean, Boolean)] { case (current, isEnabled) =>
            if isEnabled then onToggle(!current)
        }
        div(
            cls := "rr-switch",
            cls("is-on") <-- checked,
            role := "switch",
            aria.checked <-- checked.map(_.toString),
            tabIndex := 0,
            span(cls := "rr-switch-knob"),
            onClick.compose(_.sample(state)) --> flip,
            onKeyDown
                .filter(e => e.key == " " || e.key == "Enter")
                .preventDefault
                .compose(_.sample(state)) --> flip
        )
    end toggle
end SettingsPage
