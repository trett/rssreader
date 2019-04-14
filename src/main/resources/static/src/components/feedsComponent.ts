import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import {Route} from "vue-router";
import {FeedItem, NetworkService} from "../services/networkService";

@Component({
    template: `
        <ul>
            <li v-for="feedItem in data" class="my-3">
                <a  :href="feedItem.link"
                    @click="markRead(feedItem.id)"
                    target="_blank"
                    v-bind:class="{read: feedItem.read, new: !feedItem.read}">
                    {{ feedItem.title }}
                </a>
                <p class="font-weight-black body-2">{{ feedItem.pubDate }}</p>
                <div class="body-2" v-html="feedItem.description"></div>
            </li>
        </ul>
    `
})
export default class FeedsComponent extends Vue {

    private data: Array<FeedItem> = [];

    async beforeMount(): Promise<void> {
        await this.getChannels(Number(this.$route.params.id));
    }

    @Watch('$route')
    private async onRouteUpdate(to: Route, from: Route, next: Function): Promise<void> {
        await this.getChannels(Number(to.params.id))
    }

    private async getChannels(id: number | null) {
        try {
            this.data = id ?
                (await NetworkService.getFeedsByChannelId(Number(id))) : (await NetworkService.getAllFeeds());
        } catch (e) {
            Vue.prototype.$setError(e.message);
        }
    }

    private async markRead(id: number): Promise<void> {
        try {
            await NetworkService.markRead(id);
        } catch (e) {
            Vue.prototype.$setError(e.message);
        }
        this.data.forEach(item => {
            if (item.id === id) {
                item.read = true;
            }
        });
    }
}