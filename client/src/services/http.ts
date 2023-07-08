import axios from "axios";
import EventBus from "../eventBus";
import router from '../router';
const http = axios.create({ baseURL: process.env.SERVER_URL });

http.interceptors.request.use(config => {
  EventBus.$emit("loading");
  return config;
});

http.interceptors.response.use((response: { status: number; data: any; }) => {
  EventBus.$emit("loadOff");
  return response.data;
}, (error: { response: any; }) => {
  EventBus.$emit("loadOff");
  const response = error.response;
  if (response.status === 301) {
    // eslint-disable-next-line @typescript-eslint/no-empty-function
    router.replace("/auth").catch(() => {});
  } else {
    EventBus.$emit("error", response ? response.data : "Unexpected server error");
    return Promise.reject(error);
  }
});

export default http;
