import axios from "axios";
import EventBus from "../eventBus";
import { OAuthService } from "./oauthService";
import Environments from "../environments"

const http = axios.create({ baseURL: Environments.SERVER_URL });

http.interceptors.request.use(config => {
    const token = localStorage.getItem("access_token");
    if (config.headers) {
        config.headers.Authorization = token as string;
        EventBus.$emit("loading");
    }
    return config;
});
http.interceptors.response.use(response => {
    EventBus.$emit("loadOff");
    return response.data;
}, error => {
    EventBus.$emit("loadOff");
    const response = error.response;
    if (response && response.status === 401) {
        OAuthService.doAuth();
    } else {
        EventBus.$emit("error", response ? response.data : "Unexpected server error");
        return Promise.reject(error);
    }
});

export default http;
