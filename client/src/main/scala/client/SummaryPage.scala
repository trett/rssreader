package client

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import client.NetworkUtils.*
import com.raquo.laminar.api.L.*
import ru.trett.rss.models.{SummaryResponse, SummarySuccess, SummaryError}

import scala.util.{Failure, Success, Try}

object SummaryPage:

    import Decoders.given

    private val model = AppState.model
    import model.*

    private case class PageState(
        summaries: List[String] = List(),
        isLoading: Boolean = true,
        totalProcessed: Int = 0,
        noFeeds: Boolean = false,
        hasMore: Boolean = false,
        funFact: Option[String] = None,
        hasError: Boolean = false
    )

    private val stateVar: Var[PageState] = Var(PageState())
    private val stateSignal = stateVar.signal
    private val loadMoreBus: EventBus[Unit] = new EventBus

    private def resetState(): Unit = stateVar.set(PageState())

    private def fetchSummaryBatch(): EventStream[Try[SummaryResponse]] =
        FetchStream
            .withDecoder(responseDecoder[SummaryResponse])
            .get("/api/summarize")
            .map {
                case Success(Some(value)) => Success(value)
                case Success(None) => Failure(new RuntimeException("Failed to decode summary response"))
                case Failure(err) => Failure(err)
            }

    private val batchObserver: Observer[Try[SummaryResponse]] = Observer {
        case Success(response) =>
            if response.noFeeds then
                stateVar.update(_.copy(
                    isLoading = false,
                    noFeeds = true,
                    hasMore = false,
                    funFact = response.funFact
                ))
            else if response.feedsProcessed > 0 then
                val (newContent, isError) = response.result match
                    case SummarySuccess(html)  => (html, false)
                    case SummaryError(message) => (message, true)

                stateVar.update(s => s.copy(
                    isLoading = false,
                    summaries = s.summaries :+ newContent,
                    hasError = isError,
                    totalProcessed = s.totalProcessed + response.feedsProcessed,
                    hasMore = response.hasMore
                ))
                Home.refreshUnreadCountBus.emit(())
            else
                stateVar.update(_.copy(isLoading = false))

        case Failure(err) =>
            stateVar.update(_.copy(isLoading = false, hasError = true))
            handleError(err)
    }

    def render: Element =
        resetState()
        val initialFetch = fetchSummaryBatch()
        val settingsFetch = model.ensureSettingsLoaded()

        div(
            cls := "main-content",
            initialFetch --> batchObserver,
            settingsFetch.collectSuccess --> settingsVar.writer,
            onMountBind { ctx =>
                loadMoreBus.events.flatMapSwitch { _ =>
                    stateVar.update(_.copy(isLoading = true))
                    fetchSummaryBatch()
                } --> batchObserver
            },
            Card(
                _.slots.header := CardHeader(
                    _.titleText := "AI Summary",
                    _.slots.avatar := Icon(_.name := IconName.`feeder-arrow`)
                ),
                div(
                    padding.px := 16,
                    fontFamily := "var(--sapFontFamily)",
                    fontSize := "15px !important",
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
                        if state.noFeeds then
                            val validFunFact = state.funFact.filter(f =>
                                f.nonEmpty && !f.contains("All caught up") && !f.contains("No new feeds")
                            )
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
                                validFunFact
                                    .map(fact =>
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
                                    .getOrElse(emptyNode)
                            )
                        else emptyNode
                    },
                    div(
                        children <-- stateSignal.map { state =>
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
                        }
                    ),
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
                        if !state.isLoading && state.summaries.nonEmpty && !state.noFeeds then
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
