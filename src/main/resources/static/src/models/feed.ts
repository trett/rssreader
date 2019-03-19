import {FeedItem} from "./feedItem";

export type Feed = {

    id: number,

    title: string;

    link: string;

    feedItems: Array<FeedItem>;
}