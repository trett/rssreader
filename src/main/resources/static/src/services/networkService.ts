import axios, {AxiosRequestConfig, AxiosResponse} from "axios";
import EventBus from "../eventBus";

export class NetworkService {

    public static async getChannels(): Promise<IChannel[]> {
        const response = await this.httpGet("/channel/all");
        return response.data;
    }

    public static async addChannel(channel: string): Promise<number> {
        const response = await this.httpPost("/channel/add", channel);
        return response.data;
    }

    public static async updateChannels(): Promise<void> {
        await this.httpGet("channel/refresh");
    }

    public static async deleteChannel(channelId: string): Promise<void> {
        await this.httpPost("/channel/delete", channelId);
    }

    public static async getAllFeeds(): Promise<FeedItem[]> {
        const response = await this.httpGet("/feed/all");
        return response.data;
    }

    public static async getFeedsByChannelId(id: string): Promise<FeedItem[]> {
        const response = await this.httpGet(`/feed/get/${id}`);
        return response.data;
    }

    public static async markRead(ids: string[]): Promise<void> {
        await this.httpPost("/feed/read", JSON.stringify(ids));
    }

    public static async getSettings(): Promise<string> {
        const response = await this.httpGet("/settings");
        return JSON.stringify(response.data);
    }

    public static async saveSettings(settings: string): Promise<void> {
        await this.httpPost("/settings", settings);
    }

    public static async deleteOldItems(): Promise<void> {
        await this.httpPost("/feed/deleteOldItems");
    }

    private static async httpPost(path: string, data?: string): Promise<AxiosResponse<any>> {
        const configInit: AxiosRequestConfig = {
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json",
                "X-Requested-With": "XMLHttpRequest",
            },
            method: "POST",
            data,
        };
        return this.sendRequest(path, configInit);
    }

    private static async httpGet(path: string): Promise<AxiosResponse<any>> {
        const configInit: AxiosRequestConfig = {
            headers: {
                "X-Requested-With": "XMLHttpRequest",
            },
            method: "GET",
        };
        return this.sendRequest(path, configInit);
    }

    private static async sendRequest(path: string, configInit: AxiosRequestConfig): Promise<AxiosResponse<any>> {
        const instance = axios.create({
            baseURL: "/api",
        });

        instance.interceptors.request.use(config => {
            EventBus.$emit("loading");
            return config;
        });
        instance.interceptors.response.use(response => {
            EventBus.$emit("loadOff");
            return response;
        });
        const answer = await instance(path, configInit);
        return answer;
    }
}

export type FeedItem = IChannel & {
    pubDate: string,
    description: string,
    read: boolean,
    channelId: number,
};

export interface IChannel {
    id: string;
    title: string;
    link: string;
    feedItems: FeedItem[];
}

export interface ISettings {
    hideRead: boolean;
    deleteAfter: number;
}
