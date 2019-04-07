import {Channel} from "./channel";

export type FeedItem = Channel & {

    pubDate: string;

    description: string;
}