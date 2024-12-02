package client2

object NetworkUtils {
  
  val HOST = "API_HOST"

  val JSON_ACCEPT: (String, String) =
    ("Accept" -> "application/json")
  val JSON_CONTENT_TYPE: (String, String) =
    ("Content-Type" -> "application/json")
}
