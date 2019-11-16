import {AxiosRequestConfig, AxiosResponse} from "axios";
import http from "./http";

export class NetworkService {

    public static async getChannels(): Promise<IChannel[]> {
        return this.httpGet("/channel/all");
    }

    public static async addChannel(channel: string): Promise<number> {
        return this.httpPost("/channel/add", channel);
    }

    public static async updateChannels(): Promise<void> {
        await this.httpGet("channel/refresh");
    }

    public static async deleteChannel(channelId: string): Promise<void> {
        await this.httpPost("/channel/delete", channelId);
    }

    public static async getAllFeeds(): Promise<FeedItem[]> {
        return this.httpGet("/feed/all");
    }

    public static async getFeedsByChannelId(id: string): Promise<FeedItem[]> {
        return this.httpGet(`/feed/get/${id}`);
    }

    public static async markRead(ids: string[]): Promise<void> {
        await this.httpPost("/feed/read", JSON.stringify(ids));
    }

    public static async getSettings(): Promise<ISettings> {
        return this.httpGet("/settings");
    }

    public static async saveSettings(settings: ISettings): Promise<void> {
        await this.httpPost("/settings", JSON.stringify(settings));
    }

    public static async deleteOldItems(): Promise<void> {
        await this.httpPost("/feed/deleteOldItems");
    }

    private static async httpPost(path: string, data?: string): Promise<any> {
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

    private static async httpGet(path: string): Promise<any> {
        const configInit: AxiosRequestConfig = {
            headers: {
                "X-Requested-With": "XMLHttpRequest",
            },
            method: "GET",
        };
        return this.sendRequest(path, configInit);
    }

    private static async sendRequest(path: string, configInit: AxiosRequestConfig): Promise<any> {
        return http(path, configInit);
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
