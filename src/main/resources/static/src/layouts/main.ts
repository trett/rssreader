import Vue from "vue";
import Component from "vue-class-component";
import {Watch} from "vue-property-decorator";
import AlertComponent from "../components/alertComponent";
import EventBus from "../eventBus";
import {Channel, NetworkService} from "../services/networkService";

@Component({
    components: {
        alert: AlertComponent
    },
    template: `
<v-app>
    <v-navigation-drawer dark app v-model="drawer">
        <v-list dense class="pt-0">
            <v-list-tile to="/settings" exact>
                <v-list-tile-avatar>
                    <v-icon>fa-pencil-square-o</v-icon>
                </v-list-tile-avatar>
                <v-list-tile-content>Settings</v-list-tile-content>
            </v-list-tile>
            <v-list-tile key="all" :to="{path: '/channel/all', query: {t: + new Date()}}">
                <v-list-tile-avatar>
                    <v-icon>fa-rss</v-icon>
                </v-list-tile-avatar>
                <v-list-tile-content>All channels</v-list-tile-content>
            </v-list-tile>
            <v-list-tile v-for="channel in channels" :key="channel.title"
                        :to="{path: '/channel/' + channel.id, query: {t: + new Date()}}">
                <v-list-tile-avatar>
                    <v-icon>fa-rss</v-icon>
                </v-list-tile-avatar>
                <v-list-tile-content>
                    <v-list-tile-title>{{ channel.title }}</v-list-tile-title>
                </v-list-tile-content>
                <v-list-tile-action>
                    <v-btn icon ripple @click="deleteChannel(channel)">
                        <v-icon color="grey lighten-1">fa-trash-o</v-icon>
                    </v-btn>
                </v-list-tile-action>
            </v-list-tile>
        </v-list>
    </v-navigation-drawer> 
    <v-toolbar app dense>
        <v-btn icon @click.stop="drawer = !drawer">
            <v-icon>fa-bars</v-icon>
        </v-btn>
        <v-btn icon @click="refresh()">
              <v-icon>cached</v-icon>
        </v-btn>
        <v-dialog v-model="dialog" persistent max-width="500"> 
            <template v-slot:activator="{ on }">
                <v-btn icon v-on="on">
                    <v-icon dark>add</v-icon>
                </v-btn>
            </template> 
            <v-card>
                <v-container>
                    <v-card-title class="headline">Add feed</v-card-title>
                        <v-text-field v-model="newChannel" label="URL" style="margin: 0 5px 0 5px" single-line autofocus>
                        </v-text-field> 
                    <v-card-actions>
                        <v-spacer></v-spacer>
                        <v-btn color="green darken-1" flat @click="dialog = false;">Cancel</v-btn>
                        <v-btn color="green darken-1" flat @click="dialog = false; addChannel()">Add</v-btn>
                    </v-card-actions>
                </v-container>
            </v-card> 
        </v-dialog>
        <v-spacer></v-spacer>
        <v-btn icon @click="markAllAsRead()">
            <v-icon>fa-check</v-icon>
        </v-btn>
    </v-toolbar>
    <v-content>
        <v-container fluid>
            <div class="text-xs-center" v-if="loading">
                <v-progress-circular slot="extension" :indeterminate="true" class="ma-0"></v-progress-circular> 
            </div>
            <alert></alert>
            <confirm ref="confirm"></confirm>
            <router-view></router-view>
        </v-container>
    </v-content>
</v-app>
`
})
export default class Main extends Vue {

    private channels: Array<Channel> = [];

    private newChannel = "";
    
    private loading = false;

    private dialog = false;

    private drawer = null;

    async beforeMount(): Promise<void> {
        try {
            this.channels = await NetworkService.getChannels();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    };
    
    mounted(): void {
        EventBus.$on("loading", () => this.loading = true);
        EventBus.$on("loadOff", () => this.loading = false);
    }

    private async refresh(): Promise<void> {
        try {
            await NetworkService.updateChannels();
            return this.update();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async addChannel(): Promise<void> {
        try {
            const channelId = await NetworkService.addChannel(this.newChannel);
            await this.update();
            this.$router.push({path: `/channel/${channelId}`});
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async markAllAsRead(): Promise<void> {
        EventBus.$emit("markAllAsRead");
    }

    private async deleteChannel(channel: Channel): Promise<void> {
        if (!confirm(`Do you really want to delete ${channel.title}?`)) {
            return;
        }
        try {
            await NetworkService.deleteChannel(channel.id);
            this.$router.push({path: "/"});
            await this.update();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
    }

    private async update(): Promise<void> {
        try {
            this.channels = await NetworkService.getChannels();
        } catch (e) {
            EventBus.$emit("error", e.message);
        }
        EventBus.$emit("updateFeeds");
    }

    @Watch("dialog")
    private onDialogVisibleChange(val: any, oldVal: any): void {
        if (val) {
            this.newChannel = "";
        }
    }
}
