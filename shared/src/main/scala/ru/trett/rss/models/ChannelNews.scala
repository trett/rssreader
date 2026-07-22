package ru.trett.rss.models

/** News items for a single channel, used by the MCP `get_news_by_date` tool when returning all
  * channels at once. Grouping keeps each feed's items together so a single-language block can be
  * translated accurately.
  */
final case class ChannelNews(channelId: Long, channelTitle: String, items: List[FeedItemData])
