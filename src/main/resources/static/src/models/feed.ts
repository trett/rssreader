import {FeedItem} from "./feedItem";

export type Feed = {

    title: string;

    link: string;

    feedItems: Array<FeedItem>;
}