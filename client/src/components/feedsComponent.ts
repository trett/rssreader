import Vue from "vue";
import Component from "vue-class-component";
import { Prop } from "vue-property-decorator";
import { FeedEntity, NetworkService } from "../services/networkService";

@Component({
    template: `
        <p v-if="!data.length" class="text-center display-1" style="color: #666666">Nothing to read :( </p>
        <v-container v-else fluid grid-list-md pa-0>
            <v-layout row wrap>
                <template v-for="feedEntity in data">
                    <v-flex xs12>
                        <v-card tag="a"
                                @click="markRead([feedEntity.id]); window.open(feedEntity.link, '_blank')"
                                elevation="3"
                                v-bind:class="{read: feedEntity.read}"
                                v-ripple="false"
                                class="feed">
                            <v-card-title class="title" style="word-break: break-word;">
                                {{ feedEntity.title || (feedEntity.description.substring(0, 50) + '...') }}
                            </v-card-title>
                            <v-card-text>
                                <div v-bind:class="{read: feedEntity.read, unread: !feedEntity.read}"
                                    v-html="feedEntity.description">
                                </div>
                            </v-card-text>
                            <v-card-actions class="body-2" style="display: block;">
                                <v-chip class="ma-2" small>{{ feedEntity.pubDate }}</v-chip> 
                                <v-chip v-if="!!feedEntity.channelTitle" class="ma-2" small outlined color="primary">{{ feedEntity.channelTitle }}</v-chip>
                            </v-card-actions>
                        </v-card>
                    </v-flex>
                </template>
            </v-layout>
        </v-container>
    `,
})
export default class FeedsComponent extends Vue {

    @Prop({ default: [] })
    private data: FeedEntity[] = [];

    private async markRead(ids: string[]): Promise<void> {
        await NetworkService.markRead(ids);
        this.data.forEach(item => {
            if (ids.indexOf(item.id as string) !== -1) {
                item.read = true;
            }
        });
    }
}
