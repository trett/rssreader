import axios, {AxiosRequestConfig, AxiosResponse} from "axios";
import EventBus from "../eventBus";

export class NetworkService {

    public static async getChannels(): Promise<Array<Channel>> {
        const response = await this.httpGet("/channel/all");
        return response.data;
    }

    public static async addChannel(channel: string): Promise<Number> {
        const response = await this.httpPost("/channel/add", channel);
        return response.data;
    }

    public static async updateChannels(): Promise<void> {
        await this.httpGet("channel/refresh");
    }

    public static async deleteChannel(channelId: string): Promise<void> {
        await this.httpPost("/channel/delete", channelId);
    }

    public static async getAllFeeds(): Promise<Array<FeedItem>> {
        const response = await this.httpGet('/feed/all');
        return response.data;
    }

    public static async getFeedsByChannelId(id: string): Promise<Array<FeedItem>> {
        const response = await this.httpGet(`/feed/get/${id}`);
        return response.data;
    }

    public static async markRead(ids: Array<string>): Promise<void> {
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
                "X-Requested-With": "XMLHttpRequest"
            },
            method: "POST",
            baseURL: "/api",
            data: data
        };
        return this.sendRequest(path, configInit);
    }

    private static async httpGet(path: string): Promise<AxiosResponse<any>> {
        const configInit: AxiosRequestConfig = {
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            },
            method: "GET",
            baseURL: "/api"
        };
        return this.sendRequest(path, configInit);
    }

    private static async sendRequest(path: string, configInit: AxiosRequestConfig): Promise<AxiosResponse<any>> {
        EventBus.$emit("loading");
        const response = await axios(path, configInit);
        EventBus.$emit("loadOff");
        return response;
    }
}

export type FeedItem = Channel & {
    pubDate: string,
    description: string,
    read: boolean,
    channelId: number
}

export type Channel = {
    id: string,
    title: string,
    link: string,
    feedItems: Array<FeedItem>;
}

export type Settings = {
    hideRead: boolean,
    deleteAfter: number
}
