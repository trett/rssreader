package client

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.*
import ru.trett.rss.models.SummaryEvent
import client.NetworkUtils.unsafeParseToHtmlFragment
import scala.util.{Failure, Success, Try}

object SummaryPage:

    private case class State(
        summaries: List[String] = List.empty,
        isLoading: Boolean = false,
        totalProcessed: Int = 0,
        hasMore: Boolean = false,
        funFact: Option[String] = None,
        error: Option[String] = None
    )

    def render: HtmlElement =
        val stateVar = Var(State(isLoading = true))
        val loadMoreBus = new EventBus[Int]
        var currentClose: Option[() => Unit] = None

        def cleanup(): Unit =
            currentClose.foreach(_())
            currentClose = None

        val streamObserver: Observer[Try[SummaryEvent]] = Observer {
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
                stateVar.update(_.copy(error = Some(msg), isLoading = false))
                client.NotifyComponent.errorMessage(new RuntimeException(msg))

            case Success(SummaryEvent.Done) =>
                stateVar.update(_.copy(isLoading = false))
                cleanup()

            case Failure(err) =>
                stateVar.update(_.copy(error = Some(err.getMessage), isLoading = false))
                cleanup()
                NetworkUtils.handleError(err)
        }

        def startStreaming(offset: Int)(using owner: Owner): Unit =
            cleanup()
            stateVar.update(s =>
                s.copy(
                    isLoading = true,
                    error = None,
                    // If loading more, append a placeholder for the new summary to avoid overwriting the last one
                    summaries = if offset > 0 then s.summaries :+ "" else s.summaries
                )
            )

            val (stream, close) = NetworkUtils.streamSummary(s"/api/summarize?offset=$offset")
            currentClose = Some(close)
            stream.addObserver(streamObserver)(owner)

        div(
            cls := "main-content",
            onMountUnmountCallback(
                mount = ctx =>
                    loadMoreBus.events
                        .startWith(0)
                        .foreach(offset => startStreaming(offset)(using ctx.owner))(ctx.owner),
                unmount = _ => cleanup()
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
                    // Empty / Loading State
                    child <-- stateVar.signal.map { state =>
                        if state.isLoading && state.summaries.isEmpty then
                            renderLoading("Brewing your news digest...")
                        else emptyNode
                    },
                    // Fun Fact / Done State
                    child <-- stateVar.signal.map(_.funFact).map {
                        case Some(fact) => renderFunFact(fact)
                        case None       => emptyNode
                    },
                    // Summaries List
                    div(
                        children <-- stateVar.signal
                            .map(_.summaries)
                            .splitByIndex((index, _, textSignal) =>
                                renderSummaryItem(index, textSignal, stateVar.signal)
                            )
                    ),
                    // Loading More Indicator
                    child <-- stateVar.signal.map { state =>
                        if state.isLoading && state.summaries.nonEmpty then
                            div(
                                display.flex,
                                alignItems.center,
                                justifyContent.center,
                                padding.px := 20,
                                gap.px := 10,
                                BusyIndicator(
                                    _.active := true,
                                    _.size := BusyIndicatorSize.S
                                ),
                                span("Loading more stories...")
                            )
                        else emptyNode
                    },
                    // Load More Button
                    child <-- stateVar.signal.map { state =>
                        if !state.isLoading && state.summaries.nonEmpty && state.funFact.isEmpty
                        then
                            renderLoadMore(state, loadMoreBus)
                        else emptyNode
                    }
                )
            )
        )

    private def renderLoading(text: String): HtmlElement =
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
                text
            )
        )

    private def renderFunFact(fact: String): HtmlElement =
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

    private def renderSummaryItem(
        index: Int,
        textSignal: Signal[String],
        stateSignal: Signal[State]
    ): HtmlElement =
        div(
            child <-- textSignal.map(unsafeParseToHtmlFragment),
            child <-- stateSignal.map { state =>
                if index < state.summaries.length - 1 then
                    hr(
                        marginTop.px := 20,
                        marginBottom.px := 20,
                        border := "none",
                        borderTop := "1px solid var(--sapContent_ForegroundBorderColor)"
                    )
                else emptyNode
            }
        )

    private def renderLoadMore(state: State, bus: EventBus[Int]): HtmlElement =
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
            if state.hasMore && state.error.isEmpty then
                Button(
                    _.design := ButtonDesign.Emphasized,
                    _.icon := IconName.download,
                    "Load more news",
                    _.events.onClick.mapTo(state.totalProcessed) --> bus.writer
                )
            else emptyNode
        )
