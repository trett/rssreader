import {FeedItem} from "./feedItem";

export type Channel = {

    id: number,

    title: string;

    link: string;

    feedItems: Array<FeedItem>;
}