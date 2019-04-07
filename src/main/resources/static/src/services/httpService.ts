export default class HttpService {

    public static async sendRequest(path: string, configInit?: RequestInit): Promise<any> {
        const response = await fetch(path, configInit);
        if (response.status !== 200) {
            throw Error(response.headers.get('errorMessage') as string);
        }
        return response;
    }
}