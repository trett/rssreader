import http from "./http";

export class NetworkService {

    private static readonly JSON_REQUEST_HEADERS = {
        "Accept": "application/json",
        "Content-Type": "application/json",
    };

    private static readonly PLAIN_REQUEST_HEADERS = {
        "Accept": "application/json",
        "Content-Type": "text/plain",
    };

    public static async getChannels(): Promise<IChannel[]> {
        return http.get("/channel/all");
    }

    public static async addChannel(url: string): Promise<number> {
        return http.post("/channel/add", url, { headers: this.PLAIN_REQUEST_HEADERS });
    }

    public static async updateChannels(): Promise<void> {
        await http.get("channel/refresh");
    }

    public static async deleteChannel(channelId: string): Promise<void> {
        await http.post("/channel/delete", channelId, { headers: this.JSON_REQUEST_HEADERS });
    }

    public static async getAllFeeds(): Promise<FeedEntity[]> {
        return http.get("/feed/all");
    }

    public static async getFeedsByChannelId(id: string): Promise<FeedEntity[]> {
        return http.get(`/feed/get/${id}`);
    }

    public static async markRead(ids: string[]): Promise<void> {
        await http.post("/feed/read", JSON.stringify(ids), { headers: this.JSON_REQUEST_HEADERS });
    }

    public static async getSettings(): Promise<ISettings> {
        return http.get("/settings");
    }

    public static async saveSettings(settings: ISettings): Promise<void> {
        await http.post("/settings", JSON.stringify(settings), { headers: this.JSON_REQUEST_HEADERS });
    }

    public static async deleteOldItems(): Promise<void> {
        await http.post("/feed/deleteOldItems");
    }
}

export type FeedEntity = {
    id: string,
    link: string,
    title: string,
    pubDate: string,
    description: string,
    read: boolean,
    channelTitle: string
};

export interface IChannel {
    id: string;
    title: string;
}

export interface ISettings {
    hideRead: boolean;
    deleteAfter: number;
}
