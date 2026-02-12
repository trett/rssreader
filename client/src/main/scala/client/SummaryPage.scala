package client

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import client.NetworkUtils.*
import com.raquo.laminar.api.L.*
import ru.trett.rss.models.SummaryEvent

import scala.util.{Failure, Success}

object SummaryPage:

    private val model = AppState.model

    private case class PageState(
        summaries: List[String] = List(),
        isLoading: Boolean = true,
        totalProcessed: Int = 0,
        hasMore: Boolean = false,
        funFact: Option[String] = None,
        hasError: Boolean = false
    )

    private val stateVar: Var[PageState] = Var(PageState())
    private val stateSignal = stateVar.signal
    private val loadMoreBus: EventBus[Unit] = new EventBus

    private var currentSubscription: Option[Subscription] = None
    private var currentClose: Option[() => Unit] = None

    private def resetState(): Unit = stateVar.set(PageState())

    private def cleanup(): Unit =
        currentSubscription.foreach(_.kill())
        currentClose.foreach(_())
        currentSubscription = None
        currentClose = None

    private def startStreaming(offset: Int): Unit =
        cleanup()

        stateVar.update(s =>
            s.copy(
                isLoading = true,
                hasError = false,
                summaries = if offset > 0 then s.summaries :+ "" else s.summaries
            )
        )

        val (stream, close) = NetworkUtils.streamSummary(s"/api/summarize?offset=$offset")
        currentClose = Some(close)

        currentSubscription = Some(stream.foreach {
            case Success(SummaryEvent.Content(text)) =>
                stateVar.update(s =>
                    val newSummaries =
                        if s.summaries.isEmpty then List(text)
                        else s.summaries.init :+ (s.summaries.last + text)
                    s.copy(summaries = newSummaries)
                )

            case Success(SummaryEvent.Metadata(processed, remaining, more)) =>
                stateVar.update(s =>
                    s.copy(totalProcessed = s.totalProcessed + processed, hasMore = more)
                )
                Home.refreshUnreadCountBus.emit(())

            case Success(SummaryEvent.FunFact(text)) =>
                stateVar.update(_.copy(funFact = Some(text), isLoading = false))

            case Success(SummaryEvent.Error(msg)) =>
                stateVar.update(_.copy(hasError = true, isLoading = false))
                client.NotifyComponent.errorMessage(new RuntimeException(msg))

            case Success(SummaryEvent.Done) =>
                stateVar.update(_.copy(isLoading = false))
                cleanup()

            case Failure(err) =>
                stateVar.update(_.copy(hasError = true, isLoading = false))
                cleanup()
                handleError(err)
        }(unsafeWindowOwner))

    def render: Element =
        resetState()
        div(
            cls := "main-content",
            onMountUnmountCallback(mount = _ => startStreaming(0), unmount = _ => cleanup()),
            loadMoreBus.events.map(_ => stateVar.now().totalProcessed) --> (offset =>
                startStreaming(offset)
            ),
            Card(
                _.slots.header := CardHeader(
                    _.titleText := "AI Summary",
                    _.slots.avatar := Icon(_.name := IconName.`feeder-arrow`)
                ),
                div(
                    padding.px := 16,
                    fontFamily := "var(--sapFontFamily)",
                    fontSize.px := 15,
                    color := "var(--sapContent_LabelColor)",
                    lineHeight := "1.5",
                    child <-- stateSignal.map { state =>
                        if state.isLoading && state.summaries.isEmpty then
                            div(
                                display.flex,
                                flexDirection.column,
                                alignItems.center,
                                justifyContent.center,
                                padding.px := 60,
                                BusyIndicator(_.active := true, _.size := BusyIndicatorSize.L),
                                p(
                                    marginTop.px := 20,
                                    color := "var(--sapContent_LabelColor)",
                                    fontSize := "var(--sapFontSize)",
                                    "Brewing your news digest..."
                                )
                            )
                        else emptyNode
                    },
                    child <-- stateSignal.map { state =>
                        state.funFact match
                            case Some(fact) if fact.nonEmpty =>
                                div(
                                    padding.px := 40,
                                    textAlign.center,
                                    Title(_.level := TitleLevel.H3, "All caught up!"),
                                    p(
                                        marginTop.px := 10,
                                        marginBottom.px := 20,
                                        color := "var(--sapContent_LabelColor)",
                                        "You have no unread feeds."
                                    ),
                                    div(
                                        marginTop.px := 20,
                                        padding.px := 20,
                                        backgroundColor := "var(--sapBackgroundColor)",
                                        borderRadius.px := 8,
                                        border := "1px solid var(--sapContent_ForegroundBorderColor)",
                                        Title(_.level := TitleLevel.H5, "Did you know?"),
                                        p(marginTop.px := 10, fact)
                                    )
                                )
                            case _ => emptyNode
                    },
                    div(children <-- stateSignal.map { state =>
                        state.summaries.zipWithIndex.map { case (html, index) =>
                            div(
                                unsafeParseToHtmlFragment(html),
                                if index < state.summaries.length - 1 then
                                    hr(
                                        marginTop.px := 20,
                                        marginBottom.px := 20,
                                        border := "none",
                                        borderTop := "1px solid var(--sapContent_ForegroundBorderColor)"
                                    )
                                else emptyNode
                            )
                        }
                    }),
                    child <-- stateSignal.map { state =>
                        if state.isLoading && state.summaries.nonEmpty then
                            div(
                                display.flex,
                                alignItems.center,
                                justifyContent.center,
                                padding.px := 20,
                                gap.px := 10,
                                BusyIndicator(_.active := true, _.size := BusyIndicatorSize.S),
                                span("Loading more stories...")
                            )
                        else emptyNode
                    },
                    child <-- stateSignal.map { state =>
                        if !state.isLoading && state.summaries.nonEmpty && state.funFact.isEmpty
                        then
                            div(
                                paddingTop.px := 20,
                                display.flex,
                                flexDirection.column,
                                alignItems.center,
                                gap.px := 16,
                                Text(
                                    s"${state.totalProcessed} feeds summarized",
                                    color := "var(--sapContent_LabelColor)"
                                ),
                                if state.hasMore && !state.hasError then
                                    Button(
                                        _.design := ButtonDesign.Emphasized,
                                        _.icon := IconName.download,
                                        "Load more news",
                                        _.events.onClick.mapTo(()) --> loadMoreBus.writer
                                    )
                                else emptyNode
                            )
                        else emptyNode
                    }
                )
            )
        )
