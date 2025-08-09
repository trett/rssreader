package client

import com.raquo.laminar.api.L.*
import client.NetworkUtils.unsafeParseToHtmlFragment
import be.doeraene.webcomponents.ui5.Panel
import client.NetworkUtils.HOST
import be.doeraene.webcomponents.ui5.BusyIndicator
import be.doeraene.webcomponents.ui5.UList

object SummaryPage {

    def render: Element = {
        val response = EventStream.fromValue(s"$HOST/api/summarize").flatMapWithStatus { req =>
            FetchStream.get(req)
        }
        val isLoading = response.map(_.isPending)
        Panel(
            _.headerText := "Summary",
            BusyIndicator(
                _.active <-- isLoading,
                UList(
                    child <-- response
                        .splitStatus((resolved, _) => resolved.output, (pending, _) => "")
                        .map(unsafeParseToHtmlFragment(_))
                )
            )
        )
    }
}
