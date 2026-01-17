package client

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import client.NetworkUtils.*
import com.raquo.laminar.api.L.*
import io.circe.Decoder
import io.circe.generic.semiauto.*
import ru.trett.rss.models.{SummaryResponse, SummaryResult, SummarySuccess, SummaryError, UserSettings}

import scala.util.{Failure, Success, Try}

object SummaryPage:

    given Decoder[SummarySuccess] = deriveDecoder
    given Decoder[SummaryError] = deriveDecoder

    given Decoder[SummaryResult] =
        Decoder.instance { cursor =>
            cursor.downField("type").as[String].flatMap {
                case "success" => cursor.as[SummarySuccess]
                case "error" => cursor.as[SummaryError]
                case other => Left(io.circe.DecodingFailure(s"Unknown SummaryResult type: $other", cursor.history))
            }
        }

    given Decoder[SummaryResponse] = deriveDecoder
    given Decoder[UserSettings] = deriveDecoder

    private val model = AppState.model
    import model.*

    private val summariesVar: Var[List[String]] = Var(List())
    private val summariesSignal = summariesVar.signal
    private val isLoadingVar: Var[Boolean] = Var(true)
    private val isLoadingSignal = isLoadingVar.signal
    private val totalProcessedVar: Var[Int] = Var(0)
    private val noFeedsVar: Var[Boolean] = Var(false)
    private val noFeedsSignal = noFeedsVar.signal
    private val hasMoreVar: Var[Boolean] = Var(false)
    private val hasMoreSignal = hasMoreVar.signal
    private val funFactVar: Var[Option[String]] = Var(None)
    private val funFactSignal = funFactVar.signal
    private val hasErrorVar: Var[Boolean] = Var(false)
    private val hasErrorSignal = hasErrorVar.signal
    private val loadMoreBus: EventBus[Unit] = new EventBus

    private def resetState(): Unit =
        summariesVar.set(List())
        isLoadingVar.set(true)
        totalProcessedVar.set(0)
        noFeedsVar.set(false)
        hasMoreVar.set(false)
        funFactVar.set(None)
        hasErrorVar.set(false)

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
            isLoadingVar.set(false)

            if response.noFeeds then
                // No feeds available
                noFeedsVar.set(true)
                hasMoreVar.set(false)
                funFactVar.set(response.funFact)
            else if response.feedsProcessed > 0 then
                response.result match
                    case SummarySuccess(html) =>
                        hasErrorVar.set(false)
                        summariesVar.update(_ :+ html)
                    case SummaryError(message) =>
                        hasErrorVar.set(true)
                        summariesVar.update(_ :+ message)

                totalProcessedVar.update(_ + response.feedsProcessed)
                hasMoreVar.set(response.hasMore)
                Home.refreshUnreadCountBus.emit(())

        case Failure(err) =>
            isLoadingVar.set(false)
            hasErrorVar.set(true)
            handleError(err)
    }

    private val busySignal: Signal[(Boolean, List[String])] =
        isLoadingSignal.combineWith(summariesSignal)

    private val footerSignal: Signal[(Boolean, List[String], Boolean, Boolean, Boolean)] =
        isLoadingSignal.combineWith(summariesSignal, noFeedsSignal, hasMoreSignal, hasErrorSignal)

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
                    isLoadingVar.set(true)
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
                    child <-- busySignal.map { case (loading, summaries) =>
                        if loading && summaries.isEmpty then
                            div(
                                display.flex,
                                flexDirection.column,
                                alignItems.center,
                                justifyContent.center,
                                padding.px := 60,
                                BusyIndicator(
                                    _.active := true,
                                    _.size := BusyIndicatorSize.L
                                ),
                                p(
                                    marginTop.px := 20,
                                    color := "var(--sapContent_LabelColor)",
                                    fontSize := "var(--sapFontSize)",
                                    "Brewing your news digest..."
                                )
                            )
                        else emptyNode
                    },
                    child <-- noFeedsSignal.combineWith(funFactSignal).map {
                        case (true, funFact) =>
                            val validFunFact = funFact.filter(f =>
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
                        case _ => emptyNode
                    },
                    div(
                        children <-- summariesSignal.map(summaries =>
                            summaries.zipWithIndex.map { case (html, index) =>
                                div(
                                    unsafeParseToHtmlFragment(html),
                                    if index < summaries.length - 1 then
                                        hr(
                                            marginTop.px := 20,
                                            marginBottom.px := 20,
                                            border := "none",
                                            borderTop := "1px solid var(--sapContent_ForegroundBorderColor)"
                                        )
                                    else emptyNode
                                )
                            }
                        )
                    ),
                    child <-- busySignal.map { case (loading, summaries) =>
                        if loading && summaries.nonEmpty then
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
                    child <-- footerSignal.map { case (loading, summaries, noFeeds, hasMore, hasError) =>
                        if !loading && summaries.nonEmpty && !noFeeds then
                            div(
                                paddingTop.px := 20,
                                display.flex,
                                flexDirection.column,
                                alignItems.center,
                                gap.px := 16,
                                Text(
                                    s"${totalProcessedVar.now()} feeds summarized",
                                    color := "var(--sapContent_LabelColor)"
                                ),
                                if hasMore && !hasError then
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
