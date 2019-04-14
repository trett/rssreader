export class NetworkService {

    public static async getChannels(): Promise<Array<Channel>> {
        const response = await this.sendRequest("/channel/all");
        return response.json();
    }

    public static async addChannel(channel: string): Promise<Number> {
        const response = await this.sendRequest("/channel/add", this.buildPostRequest(channel));
        return response.json();
    }

    public static async updateChannels(): Promise<void> {
        await this.sendRequest("channel/refresh");
    }

    public static async getAllFeeds(): Promise<Array<FeedItem>> {
        const response = await this.sendRequest('/feed/all');
        return response.json();
    }

    public static async getFeedsByChannelId(id: number): Promise<Array<FeedItem>> {
        const response = await this.sendRequest(`/feed/get/${id}`);
        return response.json();
    }

    public static async markRead(id: number): Promise<void> {
        await this.sendRequest("/feed/read", this.buildPostRequest(String(id)));
    }

    public static async getSettings(): Promise<Settings> {
        const response = await this.sendRequest("/settings");
        return response.json();
    }

    public static async saveSettings(settings: string): Promise<void> {
        await this.sendRequest("/settings", this.buildPostRequest(settings));
    }

    private static async sendRequest(path: string, configInit?: RequestInit): Promise<Response> {
        const response = await fetch(path, configInit);
        if (response.status !== 200) {
            throw Error(response.headers.get("errorMessage") as string);
        }
        return response;
    }

    private static buildPostRequest(body: string): RequestInit {
        return {
            headers: {
                "Accept": "application/json",
                "Content-Type": "application/json"
            },
            method: 'POST',
            body: body
        };
    }
}

export type FeedItem = Channel & {
    pubDate: string;
    description: string;
    read: boolean;
}

export type Channel = {
    id: number,
    title: string;
    link: string;
    feedItems: Array<FeedItem>;
}

export type Settings = {
    hideRead: boolean;
}

