package client2


object NetworkUtils {

  def HOST = AppConfig.BASE_URI

  val JSON_ACCEPT: (String, String) =
    ("Accept" -> "application/json")
  val JSON_CONTENT_TYPE: (String, String) =
    ("Content-Type" -> "application/json")
}
