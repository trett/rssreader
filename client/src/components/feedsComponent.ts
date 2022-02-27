import Vue from "vue";
import Component from "vue-class-component";
import { Prop } from "vue-property-decorator";
import { FeedItem, NetworkService } from "../services/networkService";

@Component({
    template: `
        <p v-if="!data.length" class="text-center display-1" style="color: #666666">No feeds. Refresh later</p>
        <v-container v-else fluid grid-list-md pa-0>
            <v-layout row wrap>
                <template v-for="feedItem in data">
                    <v-flex xs12>
                        <v-card tag="a"
                                @click="markRead([feedItem.id]); window.open(feedItem.link, '_blank')"
                                elevation="3"
                                v-bind:class="{read: feedItem.read}"
                                v-ripple="false"
                                class="feed">
                            <v-card-title class="title" style="word-break: break-word;">
                                {{ feedItem.title || (feedItem.description.substring(0, 50) + '...') }}
                            </v-card-title>
                            <v-card-text>
                                <div v-bind:class="{read: feedItem.read, unread: !feedItem.read}"
                                    v-html="feedItem.description">
                                </div>
                            </v-card-text>
                            <v-card-actions class="body-2">{{feedItem.pubDate}}</v-card-actions>
                        </v-card>
                    </v-flex>
                </template>
            </v-layout>
        </v-container>
    `,
})
export default class FeedsComponent extends Vue {

    @Prop()
    private data: FeedItem[] = [];

    private async markRead(ids: string[]): Promise<void> {
        await NetworkService.markRead(ids);
        this.data.forEach(item => {
            if (ids.indexOf(item.id as string) !== -1) {
                item.read = true;
            }
        });
    }
}
