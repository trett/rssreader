import axios from "axios";
import EventBus from "../eventBus";

const http = axios.create({
    baseURL: "/api",
});
http.interceptors.response.use(response => {
    EventBus.$emit("loadOff");
    return response.data;
}, error => {
    if (401 === error.response.status) {
        // TODO: temp hack to refresh auth
        window.location.reload(true);
    } else {
        throw Error(error.response.headers["error-message"] as string);
    }
});
http.interceptors.request.use(config => {
    EventBus.$emit("loading");
    return config;
});

export default http;
