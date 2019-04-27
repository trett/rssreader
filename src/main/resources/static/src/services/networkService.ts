export class NetworkService {

    public static async getChannels(): Promise<Array<Channel>> {
        const response = await this.httpGet("/channel/all");
        return response.json();
    }

    public static async addChannel(channel: string): Promise<Number> {
        const response = await this.httpPost("/channel/add", channel);
        return response.json();
    }

    public static async updateChannels(): Promise<void> {
        await this.httpGet("channel/refresh");
    }

    public static async deleteChannel(channelId: string): Promise<void> {
        await this.httpPost("/channel/delete", channelId);
    }

    public static async getAllFeeds(): Promise<Array<FeedItem>> {
        const response = await this.httpGet('/feed/all');
        return response.json();
    }

    public static async getFeedsByChannelId(id: string): Promise<Array<FeedItem>> {
        const response = await this.httpGet(`/feed/get/${id}`);
        return response.json();
    }

    public static async markRead(ids: Array<string>): Promise<void> {
        await this.httpPost("/feed/read", JSON.stringify(ids));
    }

    public static async getSettings(): Promise<string> {
        const response = await this.httpGet("/settings");
        return response.text();
    }

    public static async saveSettings(settings: string): Promise<void> {
        await this.httpPost("/settings", settings);
    }

    public static async deleteOldItems(): Promise<void> {
        await this.httpPost("/feed/deleteOldItems");
    }

    private static async httpPost(path: string, body?: string): Promise<Response> {
        const configInit = {
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json"
            },
            method: "POST",
            body: body,
            redirect: "follow",
            credentials: "include"
        };
        return this.sendRequest(path, configInit);
    }

    private static async httpGet(path: string): Promise<Response> {
        const configInit = {
            method: "GET",
            mode: "no-cors",
            redirect: "follow",
            credentials: "include"
        };
        return this.sendRequest(path, configInit);
    }

    private static async sendRequest(path: string, configInit: any): Promise<Response> {
        const response = await fetch(path, configInit);
        if (response.status !== 200) {
            throw Error(response.headers.get("errorMessage") as string);
        }
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